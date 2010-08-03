;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns #^{:author "Nicolas Buduroi"
       :doc "Wrapper for Java Sound API's midi package."}
  clj-audio.midi
  (:use clj-audio.utils
        [clojure.contrib.def :only [defmacro- defvar-]])
  (:import [javax.sound.midi
            MidiUnavailableException
            MidiSystem
            Receiver
            Sequence
            Transmitter]))

;;;; MidiSystem

(defmacro- return-nil-if-unavailable [& body]
  `(try ~@body
    (catch ~'MidiUnavailableException _# nil)))

(defn default-receiver
  "Returns the default receiver."
  []
  (return-nil-if-unavailable
   (MidiSystem/getReceiver)))

(defn default-sequencer
  "Returns the default sequencer. If connected? is true, the sequencer
  will be connected to the default synthesizer or receiver if
  unavailable."
  [& [connected?]]
  (return-nil-if-unavailable
   (if connected?
     (MidiSystem/getSequencer true)
     (MidiSystem/getSequencer))))

(defn default-synthesizer
  "Returns the default synthesizer."
  []
  (return-nil-if-unavailable
   (MidiSystem/getSynthesizer)))

(defn default-transmitter
  "Returns the default transmitter."
  []
  (return-nil-if-unavailable
   (MidiSystem/getTransmitter)))

(defn ->sequence
  "Returns a Sequence from the given source that can be either a file,
  an InputStream or an URL."
  [source]
  (MidiSystem/getSequence source))

(defn ->soundbank
  "Returns a Soundbank from the given source that can be either a file,
  an InputStream or an URL."
  [source]
  (MidiSystem/getSoundbank source))

(defn ->midi-file-format
  "Returns a MidiFileFormat from the given source that can be either a
  file, an InputStream or an URL."
  [source]
  (MidiSystem/getMidiFileFormat source))

;;;; MidiDevice, Transmitter and Receiver

(defn devices
  "Returns all midi devices available."
  []
  (map #(MidiSystem/getMidiDevice %)
       (MidiSystem/getMidiDeviceInfo)))

(defn device-info
  "Returns a map of information about the given midi device."
  [device]
  (info->map (.getDeviceInfo device)))

(defn- ->transmitter [o]
  (if (isa? (class o) Transmitter)
    o
    (.getTransmitter o)))

(defn- ->receiver [o]
  (if (isa? (class o) Receiver)
    o
    (.getReceiver o)))

(defn connect
  "Connects a transmitter to a receiver. Can also take objects that have
  a transmitter or a receiver."
  [transmitter receiver]
  (.setReceiver (->transmitter transmitter)
                (->receiver receiver)))

(defn open
  "Opens on the given devices, receivers or transmitters."
  [& objects]
  (doseq [o objects] (.open o)))

(defn close
  "Closes on the given devices, receivers or transmitters."
  [& objects]
  (doseq [o objects] (.close o)))

(defn receiver-proxy
  "Returns a proxy that act as a Receiver sending the received messages
  to the given receiver-function with the message and a time-stamp as
  arguments."
  [receiver-function]
  (proxy [Receiver] []
    (close [])
    (send [msg ts] (receiver-function msg ts))))

;;;; Sequencer and Sequence

(defvar- division-types (wrap-enum Sequence))

(defn empty-sequence
  "Creates an empty Sequence where division-type can be one
  of :ppq, :smpte-24, :smpte-25, :smpte-30drop or :smpte-30; resolution
  is the number of ticks by quarter note or frame. You can also specify
  a number of automatically created tracks."
  [division-type resolution & [num-tracks]]
  (let [dt (division-types division-type)]
    (if num-tracks
      (Sequence. dt resolution num-tracks)
      (Sequence. dt resolution))))

(defn sequence-info
  "Returns a map of immutable information about the given sequence."
  [sequence]
  {:division-type (.getDivisionType sequence)
   :microsecond-length (.getMicrosecondLength sequence)
   :tick-length (.getTickLength sequence)
   :resolution (.getResolution sequence)})

(defn tracks
  "Returns a list of all tracks contained in the given Sequence."
  [sequence]
  (seq (.getTracks sequence)))

(defn create-track
  "Creates a new empty track in the given Sequence."
  [sequence]
  (.createTrack sequence))

(defn delete-track
  "Deletes the specified track from the given Sequence. The track
  argument can be an index of the track and if omitted the first track
  is delete."
  [sequence & [track]]
  (.deleteTrack sequence
                (cond
                 (= track nil) (first (tracks sequence))
                 (integer? track) (nth (track sequence) track)
                 :default track)))
