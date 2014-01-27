(defproject corpus-utils "0.0.2.1"
  :description "Miscellaneous utilities to parse Japanese language corpora with Clojure"
  :url "https://github.com/borh/corpus-utils.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]

                 [clj-mecab "0.3.0"]
                 [cc.qbits/knit "0.2.1"]

                 [fast-zip "0.3.0"]
                 [org.clojure/data.csv "0.1.2"]
                 [me.raynes/fs "1.4.5"]

                 [org.clojure/core.typed "0.2.21"]
                 [prismatic/schema "0.2.0"]
                 [prismatic/plumbing "0.2.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :jvm-opts ["-server"]
  :core.typed {:check [corpus-utils.document]}
  :main ^{:skip-aot true} corpus-utils.core)
