(ns corpus-utils.utils
  (:require [schema.core :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:import [org.apache.commons.compress.compressors.xz XZCompressorInputStream]
           [java.net URL]))

(s/defn read-tsv-xz :- [[s/Str]]
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

(s/defn read-tsv :- [[s/Str]]
  [file :- s/Str
   header? :- Boolean]
  (let [records (with-open [r (io/reader file)]
                  (doall (csv/read-csv r :separator \tab :quote 0)))]
    (vec
     (if header?
       (drop 1 records)
       records))))

(s/defn read-csv :- [[s/Str]]
  [file :- s/Str
   header? :- Boolean]
  (let [records (with-open [r (io/reader file)]
                  (doall (csv/read-csv r :separator \, :quote \")))]
    (vec
     (if header?
       (drop 1 records)
       records))))

(s/defn read-tsv-URL :- [[s/Str]]
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
