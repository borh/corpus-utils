(defproject corpus-utils "0.1.0"
  :description "Miscellaneous utilities to parse Japanese language corpora with Clojure"
  :url "https://github.com/borh/corpus-utils.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 [clj-mecab "0.4.0"]

                 [fast-zip "0.4.0"]
                 [org.clojure/data.csv "0.1.2"]
                 [me.raynes/fs "1.4.5"]
                 [org.apache.commons/commons-compress "1.8.1"]
                 [org.tukaani/xz "1.5"]
                 ;;[http-kit "2.1.18"]

                 [prismatic/schema "0.2.3"]
                 [prismatic/plumbing "0.3.1"]]
  :profiles {:dev {:dependencies [[compliment "0.1.0"]]}}
  :min-lein-version "2.0.0"
  :resource-paths ["data"]
  :jvm-opts ["-server"]
  :main ^{:skip-aot true} corpus-utils.text)
