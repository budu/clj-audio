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
  (:import [javax.sound.sampled
            AudioInputStream
            AudioSystem
            TargetDataLine]))

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

(defmethod ->stream TargetDataLine
  [line & _]
  (AudioInputStream. line))

(defmethod ->stream java.io.InputStream
  [stream fmt length]
  (AudioInputStream. stream fmt length))
