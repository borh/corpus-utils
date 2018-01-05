(ns corpus-utils.kokken-test
  (:require [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [schema.core :as s]
            [corpus-utils.kokken :refer :all]))

(s/with-fn-validation (parse-document-seq (fs/expand-home "~/Corpora/taiyo-corpus/XML/t189506.xml")))
(s/with-fn-validation (parse-document-seq (fs/expand-home "~/Corpora/josei_xml/xml/189505Z.xml")))
(s/with-fn-validation (parse-document-seq (fs/expand-home "~/Corpora/meiroku_xml/m187531.xml")))
(s/with-fn-validation (parse-document-seq (fs/expand-home "~/Corpora/kokumin_xml/k188709.xml")))
