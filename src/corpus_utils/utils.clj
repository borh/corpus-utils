(ns corpus-utils.utils
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:import [org.apache.commons.compress.compressors.xz XZCompressorInputStream]
           [java.net URL]))

(defn read-tsv-xz
  [file header?]
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

(s/fdef read-tsv-xz
  :args (s/cat :file #(instance? URL %) :header? boolean?)
  :ret (s/coll-of (s/coll-of string?)))

(defn read-tsv
  [file header?]
  (let [records (with-open [r (io/reader file)]
                  (doall (csv/read-csv r :separator \tab :quote 0)))]
    (vec
      (if header?
        (drop 1 records)
        records))))

(s/fdef read-tsv
  :args (s/cat :file string? :header? boolean?)
  :ret (s/coll-of (s/coll-of string?)))

(defn read-csv
  [file header?]
  (let [records (with-open [r (io/reader file)]
                  (doall (csv/read-csv r :separator \, :quote \")))]
    (vec
      (if header?
        (drop 1 records)
        records))))

(s/fdef read-csv
  :args (s/cat :file string? :header? boolean?)
  :ret (s/coll-of (s/coll-of string?)))

(defn read-tsv-URL
  [file header?]
  (let [records (with-open [r (io/reader (io/input-stream file))]
                  (doall (csv/read-csv r :separator \tab :quote 0)))]
    (vec
      (if header?
        (drop 1 records)
        records))))

(s/fdef read-tsv-URL
  :args (s/cat :file #(instance? URL %) :header? boolean?)
  :ret (s/coll-of (s/coll-of string?)))

(defn write-tsv [file-name header map-seq]
  (with-open [out-file (io/writer file-name)]
    (clojure.data.csv/write-csv out-file [header] :separator \tab) ; header
    (doseq [kv map-seq]
      (clojure.data.csv/write-csv out-file [kv] :separator \tab))))

(defn write-tsv-map [file-name map-seq]
  (with-open [out-file (io/writer file-name)]
    (let [ks (keys (first map-seq))]
      (clojure.data.csv/write-csv out-file [(conj ks "word")] :separator \tab) ; header
      (doseq [[k map-vals] map-seq]
        (clojure.data.csv/write-csv out-file [(concat [k] (mapv map-vals ks))] :separator \tab)))))

(defn write-tsv-sparse [file-name header sparse-header map-seq]
  (with-open [out-file (io/writer file-name)]
    (clojure.data.csv/write-csv out-file [(apply conj header sparse-header)] :separator \tab) ; header
    (doseq [[k vs] map-seq]
      (clojure.data.csv/write-csv out-file [(apply conj k (for [sh sparse-header] (get vs sh 0.0)))] :separator \tab))))
