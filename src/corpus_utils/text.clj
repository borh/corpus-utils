(ns corpus-utils.text
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [clj-mecab.parse :as parse]
            [corpus-utils.document :refer [UnidicMorphemeSchema DocumentSchema]]
            [clojure.core.reducers :as r]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:import [org.apache.commons.compress.compressors.xz XZCompressorInputStream]
           [java.net URL]))

(sm/defn read-tsv-xz :- [[s/Str]]
  [file :- URL
   header? :- Boolean]
  (let [records
        (with-open [r (-> file
                          io/input-stream
                          XZCompressorInputStream.
                          io/reader)]
          (doall (csv/read-csv r :separator \tab :quote 0)))]
    (vec
     (if header?
       (drop 1 records)
       records))))

(sm/defn read-tsv :- [[s/Str]]
  [file :- s/Str
   header? :- Boolean]
  (let [records (with-open [r (io/reader file)]
                  (doall (csv/read-csv r :separator \tab :quote 0)))]
    (vec
     (if header?
       (drop 1 records)
       records))))

(sm/defn read-tsv-URL :- [[s/Str]]
  [file :- URL
   header? :- Boolean]
  (let [records (with-open [r (io/reader (io/input-stream file))]
                  (doall (csv/read-csv r :separator \tab :quote 0)))]
    (vec
     (if header?
       (drop 1 records)
       records))))

(defn write-tsv [file-name header map-seq]
  (with-open [out-file (io/writer file-name)]
    (clojure.data.csv/write-csv out-file [header] :separator \tab) ; header
    (doseq [kv map-seq]
      (clojure.data.csv/write-csv out-file [kv] :separator \tab))))

(defn write-tsv-map [file-name map-seq]
  (with-open [out-file (io/writer file-name)]
    (let [ks (keys (second (first map-seq)))]
      (clojure.data.csv/write-csv out-file [(conj ks "word")] :separator \tab) ; header
      (doseq [[k map-vals] map-seq]
        (clojure.data.csv/write-csv out-file [(concat [k] (mapv map-vals ks))] :separator \tab)))))

(defn write-tsv-sparse [file-name header sparse-header map-seq]
  (with-open [out-file (io/writer file-name)]
    (clojure.data.csv/write-csv out-file [(apply conj header sparse-header)] :separator \tab) ; header
    (doseq [[k vs] map-seq]
      (clojure.data.csv/write-csv out-file [(apply conj k (for [sh sparse-header] (get vs sh 0.0)))] :separator \tab))))


(sm/defn parse-document :- [s/Str] ;; [UnidicMorphemeSchema]
  [doc :- DocumentSchema
   token-fn :- clojure.lang.IFn]
  (->> doc
       :paragraphs
       (r/mapcat :sentences)
       (r/map parse/parse-sentence)
       (r/reduce (fn ;; mecab is not thread-safe so we cannot use fold; consider core.async wrapper (using executor-based wrapper for now) (foldcat??)
                   ([] []) ;; FIXME ending with long seq of "/" ???? -> fold was causing us trouble!
                   ([a b] (into a (r/map token-fn b)))))))
