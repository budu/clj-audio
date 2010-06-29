;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns #^{:author "Nicolas Buduroi"
       :doc "Wrapper for Java Sound API's sampled package."}
  clj-audio.sampled
  (:use clj-audio.utils
        [clojure.contrib.def :only [defvar defvar-]])
  (:import [javax.sound.sampled
            AudioFormat
            AudioSystem
            BooleanControl
            CompoundControl
            EnumControl
            FloatControl
            Line
            Line$Info
            DataLine$Info
            Clip
            Mixer
            Port
            SourceDataLine
            TargetDataLine]))

;;;; AudioFormat

(defvar- encodings
  (wrap-enum javax.sound.sampled.AudioFormat$Encoding))

(defn make-format
  "Create a new AudioFormat object."
  ([sample-rate sample-size channels signed endianness]
     (AudioFormat. sample-rate
                   sample-size
                   channels
                   signed
                   (= endianness :big-endian)))
  ([encoding sample-rate sample-size frame-rate frame-size channels endianness]
     (AudioFormat. (encodings encoding)
                   sample-rate
                   sample-size
                   channels
                   frame-size
                   frame-rate
                   (= endianness :big-endian))))

(defn ->format
  "Gets the given object's AudioFormat."
  [o]
  (.getFormat o))

;;;; Mixer

(defvar *mixer*
  nil
  "Mixer to be be used by functions creating lines, if nil let the
  system decides which mixer to use.")

;;;; Line

(defvar- line-types
  {:clip   Clip
   :input  TargetDataLine
   :output SourceDataLine
   :port   Port
   :mixer  Mixer})

(defn line-info [line-type & [fmt buffer-size]]
  (let [line-type (line-types line-type)]
    (cond (and fmt buffer-size)
                   (DataLine$Info. line-type fmt buffer-size)
          fmt      (DataLine$Info. line-type fmt)
          :default (Line$Info. line-type))))

(defn make-line
  "Create a data line of the specified type (:clip, :output, :input)
  with an optional AudioFormat and buffer size."
  [line-type & [fmt buffer-size]]
  (let [info (line-info line-type fmt buffer-size)]
    (if *mixer*
      (.getLine *mixer* info)
      (AudioSystem/getLine info))))

(defmacro with-data-line
  "Open the given data line then, in a try expression, call start before
  evaluating body and call drain after. Finally close the line."
  [[binding make-line] & body]
  `(let [~binding #^DataLine ~make-line]
     (.open ~binding)
     (try
      (.start ~binding)
      (let [result# (do ~@body)]
        (.drain ~binding)
        result#)
      (finally (.close ~binding)))))

(defvar- line-events
  (wrap-enum javax.sound.sampled.LineEvent$Type
             :constants-as-keys))

(defn make-line-listener
  "Create a proxy of LineListener with the given function as update
  method. The function argument are the type of event (:open, :start,
  :stop and :close), the line and the position at which the event
  happened."
  [f]
  (proxy [javax.sound.sampled.LineListener] []
    (update [event] (f (line-events (.getType event))
                       (.getLine event)
                       (.getFramePosition event)))))

(defmacro with-line-listener
  "Create a line listener for the given line with the given
  function. Evaluate body with the added listener and remove it
  afterward."
  [line f & body]
  `(let [ll# (make-line-listener ~f)]
     (.addLineListener ~line ll#)
     ~@body
     (.removeLineListener ~line ll#)))

;;;; Line predicates

(defn open? [line] (.isOpen line))
(defn supports-control? [line ctrl-type]
  (.isControlSupported line ctrl-type))

(defn active? [data-line] (.isActive data-line))
(defn running? [data-line] (.isRunning data-line))

(defn clip? [o] (isa? (type o) Clip))
(defn port? [o] (isa? (type o) Port))
(defn source? [o] (isa? o SourceDataLine))
(defn target? [o] (isa? o TargetDataLine))

;;;; Control

(defn controls-list
  "Returns a list of controls found in the given object."
  [o]
  (seq
   (condp #(isa? %2 %1) (class o)
     CompoundControl (.getMemberControls o)
     Line (.getControls o))))

(def controls-map)

(defn ->control-pair
  "Returns a key-control pair for the given control, if it's a
  CompoundControl then convert it to a controls map."
  [ctrl]
  (let [klass (class ctrl)
        type-name (str (.getType ctrl))]
    [(-> type-name clojurize-name keyword)
     (if (isa? klass CompoundControl)
       (controls-map ctrl)
       ctrl)]))

(defn controls-map
  "Returns a map of controls found in the given object."
  [o]
  (reduce conj {}
          (map ->control-pair (controls-list o))))

(defn control-info
  "Returns a map of control's parameter."
  [control]
  (into
   (condp #(isa? %2 %1) (class control)
     FloatControl {:mid-label (.getMidLabel control)
                   :update-period (.getUpdatePeriod control)
                   :minimum (.getMinimum control)
                   :units (.getUnits control)
                   :min-label (.getMinLabel control)
                   :max-label (.getMaxLabel control)
                   :maximum (.getMaximum control)
                   :precision (.getPrecision control)}
     EnumControl {:values (.getValues control)}
     BooleanControl {:true-label (.getStateLabel control true)
                     :false-label (.getStateLabel control false)})
   {:type (.getType control)}))

(defn value
  "Get or set the value of the given control."
  [control & [new-value]]
  (if (nil? new-value)
    (.getValue control)
    (.setValue control new-value)))

;;;; File writing support

(defvar- file-types
  (wrap-enum javax.sound.sampled.AudioFileFormat$Type))

(defn supported-file-types
  "Returns a list of supported file types."
  []
  (map #(keyword (clojurize-constant-name %1))
       (AudioSystem/getAudioFileTypes)))

(defn supports-file-type?
  "Check if the given file type is supported by the system, for the
  specified (optional) audio stream."
  [file-type & [audio-stream]]
  (if audio-stream
    (AudioSystem/isFileTypeSupported (file-types file-type)
                                     audio-stream)
    (AudioSystem/isFileTypeSupported (file-types file-type))))

(defn write
  "Writes an audio-stream to the specified File or OutputStream of the
  specified file type."
  [audio-stream file-type file-or-stream]
  (AudioSystem/write audio-stream
                     (file-types file-type)
                     file-or-stream))
