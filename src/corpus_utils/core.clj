(ns corpus-utils.core
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [corpus-utils.document :refer :all]
            [corpus-utils.text :refer [parse-document]]
            [corpus-utils.bccwj :refer [document-seq]]))

#_(defn save-documents!
  []
  (doseq [doc (take 2 (document-seq "/data/BCCWJ-2012-dvd1/DOC/" "/data/BCCWJ-2012-dvd1/C-XML/VARIABLE/"))]
    (let [doc-id (-> doc :metadata :basename)]
      (->> doc
           parse-document
           (save-document! doc-id)
           doall))))
