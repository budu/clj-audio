;; Copyright (c) Nicolas Buduroi. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 which can be found in the file
;; epl-v10.html at the root of this distribution. By using this software
;; in any fashion, you are agreeing to be bound by the terms of this
;; license.
;; You must not remove this notice, or any other, from this software.

(ns #^{:author "Nicolas Buduroi"
       :doc "Utility functions used elsewhere."}
  clj-audio.utils
  (:import java.lang.reflect.Modifier))

(defn clojurize-constant-name [name]
  (.replace (.toLowerCase (str name)) "_" "-"))

(defn enum->map [names constants]
  (into {} (map #(vector (keyword (clojurize-constant-name %1)) %2)
                names
                constants)))

(defn get-static-fields [klass]
  (filter #(Modifier/isStatic (.getModifiers %))
          (.getFields klass)))

(defn wrap-enum [klass]
  (if (isa? klass Enum)
    (let [cs (.getEnumConstants klass)]
      (enum->map (map str cs) cs))
    (let [cs (get-static-fields klass)]
      (enum->map (map #(.getName %) cs)
                 (map #(.get % nil) cs)))))
