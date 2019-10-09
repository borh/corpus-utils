(ns corpus-utils.ndc
  (:require [clojure.java.io :as io]
            [corpus-utils.utils :as utils]
            [clojure.edn :as edn])
  (:import [java.io PushbackReader]))

(defonce ndc-map (edn/read (PushbackReader. (utils/xz-reader (io/resource "ndc9.edn.xz")))))
