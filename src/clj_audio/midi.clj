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
  (:use clj-audio.utils)
  (:import java.io.File
           [javax.sound.midi
            MidiUnavailableException
            MidiSystem
            Receiver
            Sequence
            Sequencer
            Sequencer$SyncMode
            Synthesizer
            Transmitter]))

;;;; MidiSystem

(defmacro return-nil-if-unavailable [& body]
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
  (if (isa? (class source) Sequencer)
    (.getSequence source)
    (MidiSystem/getSequence (file-if-string source))))

(defn ->soundbank
  "Returns a Soundbank from the given source that can be either a file,
  an InputStream or an URL."
  [source]
  (MidiSystem/getSoundbank (file-if-string source)))

(defn ->midi-file-format
  "Returns a MidiFileFormat from the given source that can be either a
  file, an InputStream or an URL."
  [source]
  (MidiSystem/getMidiFileFormat (file-if-string source)))

(defn supported-midi-file-types
  "Returns a list of supported MIDI file types for writing the given
  Sequence."
  [sequence]
  (seq (MidiSystem/getMidiFileTypes sequence)))

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
  "Connects a transmitter to one or more receivers. Can also take
  objects that have a transmitter or a receiver."
  [transmitter receiver & receivers]
  (.setReceiver (->transmitter transmitter)
                (->receiver receiver))
  (when receivers
    (apply connect transmitter (first receivers) (rest receivers))))

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

(defn- with-devices* [bindings body & [connected?]]
  (let [devices (partition 2 bindings)
        device-inits (map second devices)
        devices (map first devices)]
    `(let [~@bindings]
       (open ~@devices)
       (try
        ~(when connected?
           `(connect ~@devices))
        ~@body
        (finally (close ~@devices))))))

(defmacro with-devices
  "Binds the given devices then executes the body. Opens all devices
  before and closes them after."
  [[& bindings] & body]
  (with-devices* bindings body))

(defmacro with-connected-devices
  "Binds the given devices then executes the body. Opens and connect
  sequentially all devices before and closes them after."
  [[& bindings] & body]
  (with-devices* bindings body true))

;;;; Sequence

(def division-types ^:private (wrap-enum Sequence))

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

(defn write-midi
  "Writes the given Sequence to the specified file or OutputStream. The
  file argument can be a string. An optional midi-file-type can be
  specified, when missing, type 0 is used if the sequence has exactly
  one track, else type 1 is used."
  [sequence file & [midi-file-type]]
  (let [type (if (= 1 (count (tracks sequence))) 0 1)]
    (MidiSystem/write sequence
                      (or midi-file-type type)
                      (file-if-string file))))

(defn start-recording
  "Starts recording all tracks and channels to the given Sequence on the
  specified Sequencer."
  [sequencer sequence]
  (.setSequence sequencer sequence)
  (doseq [track (tracks sequence)]
    (.recordEnable sequencer track -1))
  (.startRecording sequencer))

(defn- record-sequence* [sequencer sequence runner]
  (start-recording sequencer sequence)
  (try
   (runner sequencer)
   (finally (.stopRecording sequencer)))
  sequence)

(defn record-sequence
  "Returns a sequence recorded from the given source. Recording length
  depends on the runner function that receive the sequencer used as
  argument. Optionally takes arguments to initialize an empty Sequence,
  see empty-sequence."
  [source runner & [division-type resolution num-tracks]]
  (let [sequence (empty-sequence (or division-type :ppq)
                                 (or resolution 20)
                                 (or num-tracks 1))]
    (with-connected-devices [source source
                             sequencer (default-sequencer)]
      (record-sequence* sequencer sequence runner))))

(defn- play-sequence* [sequencer sequence runner]
  (doto sequencer
    (.setSequence sequence)
    (.start))
  (try
   (runner sequencer)
   (finally (.stop sequencer))))

(defn play-sequence
  "Plays the given sequence. If an optional runner function is given,
  only play until it ends."
  [sequence & [runner]]
  (with-connected-devices [sequencer (default-sequencer)
                           synthesizer (default-synthesizer)]
    (play-sequence* sequencer sequence
                    (or runner
                        (fn [&_] (while (.isRunning sequencer)
                                        (Thread/sleep 30)))))))

;;;; Synthesizer

(defn synthesizer-info
  "Returns a map of information about the given Synthesizer."
  [#^Synthesizer synthesizer]
  {:latency (.getLatency synthesizer)
   :max-polyphony (.getMaxPolyphony synthesizer)})

(defn available-instruments
  "Returns all available instruments found in the given Synthesizer."
  [#^Synthesizer synthesizer]
  (seq (.getAvailableInstruments synthesizer)))

(defn loaded-instruments
  "Returns all loaded instruments found in the given Synthesizer."
  [#^Synthesizer synthesizer]
  (seq (.getLoadedInstruments synthesizer)))

(defn channels
  "Returns all channels found in the given Synthesizer."
  [#^Synthesizer synthesizer]
  (seq (.getChannels synthesizer)))

(defn remap
  "Remaps an new Instrument in place of an old one. Returns true if it
  worked."
  [#^Synthesizer synthesizer old new]
  (.remapInstrument synthesizer old new))

(defn voice-status
  "Returns the status of all voices produced by the given Synthesizer."
  [#^Synthesizer synthesizer]
  (seq (map #(hash-map :active (.active %)
                       :bank (.bank %)
                       :channel (.channel %)
                       :note (.note %)
                       :program (.program %)
                       :volume (.volume %))
            (.getVoiceStatus synthesizer))))

;;;; Soundbank

(defn soundbank-info
  "Returns a map of information about the given Soundbank."
  [soundbank]
  {:description (.getDescription soundbank)
   :instruments (seq (.getInstruments soundbank))
   :name (.getName soundbank)
   :resources (seq (.getResources soundbank))
   :vendor (.getVendor soundbank)
   :version (.getVersion soundbank)})
