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
            Clip
            SourceDataLine
            TargetDataLine]))

;;;; Audio formats

(def *default-format*
  (make-format
   {:encoding :pcm-signed
    :sample-rate 44100
    :sample-size-in-bits 16
    :frame-rate 44100
    :frame-size 4
    :channels 2
    :endianness :little-endian}))

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
                     (or fmt *default-format*)
                     (or length -1)))

(defmethod ->stream clojure.lang.IFn
  [f n]
  (let [s (java.io.ByteArrayInputStream.
           (byte-array (map (comp byte f)
                            (range n))))]
    (->stream s n)))

(defn decode
  "Convert the given stream to a data format that can be played on
  most (if not all) systems. The decoded is signed PCM in little endian
  ordering, sample size is 16 bit if not specified by the given stream
  format."
  [audio-stream]
  (let [fi  (->format-info audio-stream)
        sample-size (:sample-size-in-bits fi)
        sample-size (if (< sample-size 0)
                      16
                      sample-size)
        frame-size (* (/ sample-size 8) (:channels fi))
        fmt (make-format
             (merge fi {:encoding :pcm-signed
                        :sample-size-in-bits sample-size
                        :frame-size frame-size
                        :frame-rate (:sample-rate fi)
                        :endianness :little-endian}))]
    (convert audio-stream fmt)))

(defn write
  "Writes an audio-stream to the specified File (can be a string) or
  OutputStream of the specified file type. See the supported-file-types
  for available file types. Returns the number of bytes written."
  [audio-stream file-type file-or-stream]
  (AudioSystem/write audio-stream
                     (file-types file-type)
                     (if (string? file-or-stream)
                       (java.io.File. file-or-stream)
                       file-or-stream)))

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

(defn supports-line?
  "Check if the given mixer supports the given line type."
  [line-type mixer]
  (.isLineSupported mixer (line-info line-type)))

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
  "Play the given audio stream. Accepts an optional listener function
  that will be called when a line event is raised, taking the event
  type, the line and the stream position as arguments. Returns the
  number of bytes played."
  [audio-stream & [listener]]
  (let [line (make-line :output
                        (->format audio-stream)
                        *line-buffer-size*)
        p #(with-data-line [source line]
             (play* source audio-stream *playback-buffer-size*))]
    (if listener
      (with-line-listener line listener (p))
      (p))))

;;;; Clip playback

(defn clip
  "Creates a clip from the given audio stream and open it."
  [audio-stream]
  (doto (make-line :clip (->format audio-stream))
    (.open audio-stream)))

(defn play-clip
  "Play the given clip one or n times. Optionally takes a start and end
  position expressed in number of frames."
  [clp & [n start end]]
  (let [start (or start 0)
        end   (or end
                  (dec (.getFrameLength clp)))]
    (doto clp
      (.setFramePosition start)
      (.setLoopPoints start end)
      (.loop (dec (or n 1))))))

(defn loop-clip
  "Play the given clip continuously in a loop. Optionally takes a start
  and end position expressed in number of frames."
  [clp & [start end]]
  (play-clip clp (inc Clip/LOOP_CONTINUOUSLY) start end))

;;;; Skipping

(def *skip-inaccuracy-size* 1200)

(defn skip
  "Skip the given audio stream by the specified number of bytes."
  [audio-stream to-skip]
  (loop [total 0 remainder to-skip]
    (if (>= (+ total *skip-inaccuracy-size*) to-skip)
      audio-stream
      (let [skipped (.skip audio-stream remainder)]
        (recur (+ total skipped)
               (- remainder skipped))))))

(defn skipper
  "Returns a skip-like function that takes a ratio a the length of the
  given audio stream."
  [audio-stream]
  (let [length (.available audio-stream)]
    (fn [ratio]
      (skip audio-stream (* ratio length)))))
