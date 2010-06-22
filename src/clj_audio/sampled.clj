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
            DataLine$Info
            SourceDataLine]))

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
  {:clip javax.sound.sampled.Clip
   :output javax.sound.sampled.SourceDataLine
   :input javax.sound.sampled.TargetDataLine})

(defn make-line
  "Create a data line of the specified type (:clip, :output, :input)
  with an optional AudioFormat and buffer size."
  [line-type & [fmt buffer-size]]
  (let [info (if buffer-size
               (DataLine$Info. (line-types line-type) fmt buffer-size)
               (DataLine$Info. (line-types line-type) fmt))]
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
