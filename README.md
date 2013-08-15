
# clj-audio

This library is a general purpose audio library built on top of the Java
Sound API. It comprises wrappers for JSA's `sampled` and `midi` packages
in the corresponding namespaces. It also include a higher level API for
audio playback in the `core` namespace.

The `midi` namespace is a work in progress.

## Usage

    (use 'clj-audio.core)

### Playback

Playing a wave file.

    (play (->stream "~/test.wav"))

Playing a MP3 file, this example requires some extra libraries, see
"Installation" below.
    
    (-> (->stream "~/test.mp3") decode play)

Decoding a MP3 file and writing it to a WAVE file.

    (-> (->stream "~/test.mp3") decode (write :wave "~/test.wav"))

### Skipping Audio Streams (experimental)

Skipping a specific amount of bytes.

    (-> (->stream "~/test.mp3") (skip 1000) decode play)

Skipping a stream with a ratio of its length.

    (let [s (->stream "~/test.mp3")
          skip (skipper s)]
      (skip 0.5)
      (-> s decode play))

### Synthesizing Sounds (experimental)

Playing the resulting stream from applying the identity function from 0
to 99999. The given function output will be converted to bytes.

    (play (->stream identity 100000))

### Advanced Example

A simple headless [MP3 player] that support a basic set of command:
play, stop, pause, next, previous, random and close. To use it, you'll
need some external libraries as explained below. It should work for any
file format given the corresponding SPI and adding their extensions to
`music-file-extensions`.

## Known Issues

 * Skipping an encoded stream then writing its decoded output to a file
   using the write function will produce an empty file.
    
## Installation

This library is available on [Clojars]. To use clj-audio in your
Leiningen project, add the following dependency.

    [clj-audio "0.1.0"]

For encoded file type support, you'll need to add the corresponding
Service Provider Interface (SPI) which are simple jar files. First
you'll have to get the [Tritonus plug-ins] shared classes
(tritonus_share-*.jar) which is needed by most JSA's SPI. Then to get
MP3 support for example, you can grab the latest versions of [JLayer]
and [MP3SPI].

## License

Copyright (c) Nicolas Buduroi. All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 which can be found in the file epl-v10.html
at the root of this distribution. By using this software in any fashion,
you are agreeing to be bound by the terms of this license.

You must not remove this notice, or any other, from this software.

[Tritonus plug-ins]: http://www.tritonus.org/plugins.html
[JLayer]: http://www.javazoom.net/javalayer/sources.html
[MP3SPI]: http://www.javazoom.net/mp3spi/sources.html
[MP3 player]: http://gist.github.com/471910
[Clojars]: http://clojars.org/clj-audio
