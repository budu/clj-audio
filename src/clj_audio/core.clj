;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns #^{:author "Nicolas Buduroi"
       :doc "Clojure support for audio."}
  clj-audio.core
  (:use clj-audio.sampled
        [clojure.contrib.def :only [defvar]])
  (:import [javax.sound.sampled
            AudioInputStream
            AudioSystem
            SourceDataLine
            TargetDataLine]))

;;;; Audio formats

(defvar default-format
  (make-format :pcm-signed
               44100, 16
               44100, 4
               2
               :little-endian))

;;;; Audio input streams

(defmulti ->stream
  "Converts the given object to an AudioInputStream."
  (fn [o & _] (type o)))

(defmethod ->stream String
  [s & _]
  (AudioSystem/getAudioInputStream (java.io.File. s)))

(defmethod ->stream java.io.File
  [file & _]
  (AudioSystem/getAudioInputStream file))

(defmethod ->stream java.net.URL
  [url & _]
  (AudioSystem/getAudioInputStream url))

(defmethod ->stream TargetDataLine
  [line & _]
  (AudioInputStream. line))

(defmethod ->stream java.io.InputStream
  [stream & [length fmt]]
  (AudioInputStream. stream
                     (or fmt default-format)
                     (or length -1)))

(defmethod ->stream clojure.lang.IFn
  [f n]
  (let [s (java.io.ByteArrayInputStream.
           (byte-array (map (comp byte f)
                            (range n))))]
    (->stream s n)))

;;;; Mixer

(defn mixers
  "Returns a list of all available mixers."
  []
  (map #(AudioSystem/getMixer %)
       (AudioSystem/getMixerInfo)))

(defmacro with-mixer
  "Creates a binding to the given mixer then executes body. The mixer
  will be bound to *mixer* for use by other functions."
  [mixer & body]
  `(binding [*mixer* ~mixer]
     ~@body))

;;;; Playback

(def default-buffer-size (* 64 1024))

(defvar *line-buffer-size*
  default-buffer-size
  "Line buffer size in bytes, must correspond to an integral number of
  frames.")

(defvar *playback-buffer-size*
  default-buffer-size
  "Playback buffer size in bytes.")

(defvar *playing*
  (atom false)
  "Variable telling if play* is currectly writing to a line. If set to
  false during playback, play* will exit.")

(defn play*
  "Write the given audio stream bytes to the given source data line
  using a buffer of the given size. Returns the number of bytes played."
  [#^SourceDataLine source audio-stream buffer-size]
  (let [buffer (byte-array buffer-size)]
    (swap! *playing* (constantly true))
    (loop [cnt 0 total 0]
      (if (and (> cnt -1) @*playing*)
        (do
          (when (> cnt 0)
            (.write source buffer 0 cnt))
          (recur (.read audio-stream buffer 0 (alength buffer))
                 (+ total cnt)))
        (do
          (swap! *playing* (constantly false))
          total)))))

(defn stop
  "Stop playback for the current thread."
  []
  (swap! *playing* (constantly false)))

(defn play
  "Play the given audio stream."
  [audio-stream]
  (with-data-line [source (make-line :output
                                     (->format audio-stream)
                                     *line-buffer-size*)]
    (play* source audio-stream *playback-buffer-size*)))
