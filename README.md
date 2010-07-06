# clj-audio

Idiomatic Clojure wrapper for the Java Sound API.

It's a work in progress...

## Usage

Play a wave file.

    (play (->stream "~/test.wav"))

Play a mp3 file, this example requires some extra libraries, see
"Installation" below.
    
    (-> (->stream "~/test.mp3") decode play)

Play the resulting stream from applying the identity function from 0 to
99999. The given function output will be converted to bytes. The
resulting stream will be 100000 bytes long.

    (play (->stream identity 100000))

## Installation

It's too early for having a distribution right now, so the best way to
get clj-audio is to clone this repository and build the project using
Leiningen. I'll eventually put everything in Clojars for more
convenience.

For encoded file type support, you'll need to add the corresponding
Service Provider Interface (SPI) which are simple jar files. The
following page contains the Tritonus plug-ins shared classes
(tritonus_share-*.jar) which is needed by most JSA's SPI.

 * http://www.tritonus.org/plugins.html

For MP3 support, you can grab the latest versions of JLayer and MP3SPI
found in those pages:

 * http://www.javazoom.net/javalayer/sources.html
 * http://www.javazoom.net/mp3spi/sources.html

## License

Copyright (c) Nicolas Buduroi. All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 which can be found in the file epl-v10.html
at the root of this distribution. By using this software in any fashion,
you are agreeing to be bound by the terms of this license.

You must not remove this notice, or any other, from this software.
