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
  (:use [clojure.contrib.def :only [defvar defvar-]])
  (:import [javax.sound.sampled
            AudioSystem
            DataLine$Info
            SourceDataLine]))

(defn ->format
  "Gets the given object's AudioFormat."
  [o]
  (.getFormat o))

(defvar *mixer*
  nil
  "Mixer to be be used by functions creating lines, if nil let the
  system decides which mixer to use.")

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
