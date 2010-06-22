# clj-audio

Idiomatic Clojure wrapper for the Java Sound API.

It's a work in progress...

## Usage

Play a wave file.

    (play (->stream "g:/test.wav"))

Play the resulting stream from applying the identity function from 0 to
99999. The given function output will be converted to bytes. The
resulting stream will be 100000 bytes long.

    (play (->stream identity 100000))

## Installation

FIXME: write

## License

Copyright (c) Nicolas Buduroi. All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 which can be found in the file epl-v10.html
at the root of this distribution. By using this software in any fashion,
you are agreeing to be bound by the terms of this license.

You must not remove this notice, or any other, from this software.
