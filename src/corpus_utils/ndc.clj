(ns corpus-utils.ndc
  (:require [arachne.aristotle :as aa]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.registry :as reg]
            [plumbing.core :refer [map-vals]]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(reg/prefix 'rdfs "http://www.w3.org/2000/01/rdf-schema#")
(reg/prefix 'xsd "http://www.w3.org/2001/XMLSchema#")
(reg/prefix 'dct "http://purl.org/dc/terms/")
(reg/prefix 'skos "http://www.w3.org/2004/02/skos/core#")
(reg/prefix 'xl "http://www.w3.org/2008/05/skos-xl#")
(reg/prefix 'ndl "http://ndl.go.jp/dcndl/terms/")
(reg/prefix 'ndlsh "http://id.ndl.go.jp/auth/ndlsh/")
(reg/prefix 'ndc9 "http://jla.or.jp/data/ndc9#")
(reg/prefix 'ndc "http://jla.or.jp/data/ndc#")
(reg/prefix 'ndcv "http://jla.or.jp/vocab/ndcvocab#")

(defn load-ndc []
  (aa/read (aa/graph :simple) (io/resource "ndc9.ttl")))

(defn create-ndc-map []
  (into {}
        (q/run (load-ndc)
               '[?notation ?label]
               '[:bgp
                 [?concept :rdfs/label ?label]
                 [?concept :skos/notation ?notation]])))

(defonce ndc-map (map-vals #(string/split % #"(?!（)(．|--)") (create-ndc-map)))
