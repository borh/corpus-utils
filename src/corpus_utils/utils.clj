(ns corpus-utils.utils
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [me.raynes.fs :as fs]
            [cognitect.transit :as transit])
  (:import [org.apache.commons.compress.compressors.xz XZCompressorInputStream XZCompressorOutputStream]
           [org.apache.commons.compress.compressors.zstandard ZstdCompressorInputStream ZstdCompressorOutputStream]
           [java.net URL]
           [net.openhft.hashing LongHashFunction]
           [org.apache.commons.compress.archivers.zip ZipFile ZipArchiveEntry]
           [java.io File]
           [org.apache.commons.compress.utils IOUtils]))

(defn xz-reader [filename]
  (-> filename
      io/input-stream
      XZCompressorInputStream.
      io/reader))

(defn xz-writer [filename]
  (-> filename
      io/output-stream
      XZCompressorOutputStream.
      io/writer))

(defn zstd-input-stream [filename]
  (-> filename
      io/input-stream
      ZstdCompressorInputStream.))

(defn read-tsv-xz
  [file header?]
  (let [records
        (with-open [r (xz-reader file)]
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
  :args (s/cat :file (s/or :string string? :file #(instance? File %)) :header? boolean?)
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

(def hash-object (LongHashFunction/xx))

(defn hash-file-path [file]
  (let [basename (fs/base-name file true)
        unique-identifier (str basename "_" (.length file) "_" (.lastModified file))
        hash-long (.hashChars hash-object unique-identifier)
        hash-str (format "%02x" hash-long)
        cache-dir-path (fs/file "." "cache" (str basename "-" hash-str))]
    cache-dir-path))

(defn zipfile-cached-files
  "Returns a sequence of locally cached and compressed files in the Zip file at path. Files are cached in the 'cache'
  directory and will be recreated if deleted. Currently, all files are compressed with Zstandard compression. Note
  that file names will have their hash value appended after their basename but before any extensions."
  [path]
  (let [zf (fs/file path)
        cache-dir-path (hash-file-path path)]
    (when-not (fs/exists? cache-dir-path)
      (fs/mkdirs cache-dir-path))
    (with-open [z (ZipFile. zf)]
      (doseq [^ZipArchiveEntry ze (enumeration-seq (.getEntries z))
              :let [ze-name (fs/file cache-dir-path (.getName ze))
                    ze-path (fs/file cache-dir-path (str (fs/base-name ze-name) ".zstd"))]
              :when (and (not (fs/exists? ze-path))         ;; Do not recreate cache file if existing.
                         (not (.isDirectory ze)))]
        (with-open [zis (.getInputStream z ze)
                    wos (-> ze-path
                            io/output-stream
                            ZstdCompressorOutputStream.)]
          (IOUtils/copy zis wos))))
    (fs/glob cache-dir-path "*.zstd")))

(defn cache-processed-file!
  "Given an existing cache dir and basename, and parsed EDN data of given file, writes a compressed file containing
   the pre-processed EDN to the cache."
  [cache-dir-path basename edn-data]
  (with-open [w (-> (fs/file cache-dir-path (str basename #_"-" #_hash-str ".transit"))
                    io/output-stream
                    #_ZstdCompressorOutputStream.
                    (transit/writer :msgpack))]
    (transit/write w edn-data)))

(defn read-cached-file
  "Returns the compressed transit data in file at path."
  [path]
  (with-open [is (io/input-stream path)]
    (transit/read (transit/reader is :msgpack))))

(defn get-processed-file
  "Returns the most recently created EDN data of given basename in cache-dir-path. Note we do not hash individual files
  as it would be prohibitively expensive to find the newest file in a big file collection."
  [cache-dir-path basename]
  (let [edn-path (fs/file cache-dir-path (str basename ".transit"))
        #_(first (sort-by (fn [f] (.lastModified f)) >
                          (fs/glob cache-dir-path (str basename "-*.transit.zstd"))))]
    (if (fs/exists? edn-path)
      (read-cached-file edn-path))))

(defn clear-cache! []
  (fs/delete-dir (fs/file "." "cache")))
