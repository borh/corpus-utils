(def project 'corpus-utils)
(def version "0.2.9")

(set-env! :resource-paths #{"data" "src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "1.9.0-alpha17"]

                            [adzerk/boot-test "RELEASE" :scope "test"]
                            [org.clojure/test.check "0.10.0-alpha1" :scope "test"]
                            [adzerk/bootlaces "0.1.13" :scope "test"]

                            [clj-mecab "0.4.10"]

                            [fast-zip "0.6.1" :exclusions [com.cemerick/austin]]
                            [org.clojure/data.csv "0.1.4"]
                            [me.raynes/fs "1.4.6"]
                            [org.apache.commons/commons-compress "1.14"]
                            [org.tukaani/xz "1.6"]
                            [com.ibm.icu/icu4j "59.1"]
                            [prismatic/schema "1.1.6"]
                            [prismatic/plumbing "0.5.4"]])

(task-options!
 pom {:project     project
      :version     version
      :description "A Clojure library for idiomatic access to the Japanese Morphological Analyzer JUMAN++"
      :url         "https://github.com/borh/corpus-utils"
      :scm         {:url "https://github.com/borh/corpus-utils"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(require '[adzerk.bootlaces :refer :all])

(bootlaces! version)

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))

(deftask dev
  []
  (comp (watch) (build) (repl :init-ns 'corpus-utils.text :server true)))

(require '[adzerk.boot-test :refer [test]])
