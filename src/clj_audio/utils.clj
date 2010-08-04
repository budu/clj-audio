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

(defn clojurize-name [name]
  (apply str
    (interpose "-"
      (map #(.toLowerCase %)
           (re-seq #"[A-Z]+(?=[A-Z]|$|\s)|[A-Z][a-z]+" name)))))

(defn clojurize-constant-name [name]
  (.replace (.toLowerCase (str name)) "_" "-"))

(defn get-static-fields [klass]
  (filter #(Modifier/isStatic (.getModifiers %))
          (.getFields klass)))

(defn enum->map [names constants & [constants-as-keys]]
  (into {} (map #(let [n (keyword (clojurize-constant-name %1))
                       c %2]
                   (if constants-as-keys
                     (vector c n)
                     (vector n c)))
                names
                constants)))

(defn wrap-enum [klass & [constants-as-keys]]
  (if (isa? klass Enum)
    (let [cs (.getEnumConstants klass)]
      (enum->map (map str cs) cs
                 constants-as-keys))
    (let [cs (get-static-fields klass)]
      (enum->map (map #(.getName %) cs)
                 (map #(.get % nil) cs)
                 constants-as-keys))))

(defn info->map [info]
  {:vendor (.getVendor info)
   :name (.getName info)
   :version (.getVersion info)
   :description (.getDescription info)})

(defn file-if-string [file]
  (if (string? file)
    (java.io.File. file)
    file))
