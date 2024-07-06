(ns site
  "Namespace for building Adorn's documentation."
  (:require [dev.onionpancakes.chassis.core :as c]
            [clojure.walk :as walk]
            [site.fabricate.adorn :as adorn]
            [site.functions :as site-fns]
            [site.api-tools :as api-tools]
            [cybermonday.core :as md]))

(def out-dir "dev/html/")

(def imports
  (list
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link
    {:rel "preconnect" :crossorigin true :href "https://fonts.gstatic.com"}]
   [:link
    {:rel "stylesheet"
     :href
     "https://fonts.googleapis.com/css2?family=Eczar:wght@400..800&family=Newsreader:ital,opsz,wght@0,6..72,200..800;1,6..72,200..800&family=Recursive:slnt,wght,CASL,CRSV,MONO@-15..0,300..1000,0..1,0..1,0..1&display=swap"}]
   [:link
    {:href "https://unpkg.com/normalize.css@8.0.1/normalize.css"
     :rel  "stylesheet"}]
   [:link {:rel "stylesheet" :href "/main.css"}]
   ;; TODO: get SVG icon working
   #_[:link {:rel "icon" :type "image/svg"}]
   [:link {:rel "icon" :type "image/png" :href "/icon.png"}]))

(defn convert-pre
  [[tag attrs pre-contents :as pre]]
  (if (and (string? pre-contents) (re-find #"language-clojure" pre-contents))
    [tag attrs (adorn/clj->hiccup pre-contents)]
    pre))

(defn walk-hiccup [h] (walk/postwalk convert-pre h))

(defn md-page
  [{:keys [body frontmatter] :as data}]
  [c/doctype-html5
   [:html
    [:head
     [:title
      (:title frontmatter
              "Adorn: Extensible conversion of Clojure code to Hiccup forms")]
     imports [:body [:main [:article (:body data)]]]]]])

(defn convert-pre
  [[tag attrs pre-contents :as pre]]
  (if (= "clojure" (:language attrs))
    [:pre [:code {:class "language-clojure"} (adorn/clj->hiccup pre-contents)]]
    [:pre [:code {:class (str "language-" (:language attrs))}] pre-contents]))

(defn convert-task-list
  [[tag attrs [_ _attrs & contents]]]
  [:li [:input {:disabled true :checked (:checked? attrs) :type "checkbox"}] " "
   (apply list contents)])

(defn unparagraph-img
  "if the given element is a paragraph containing only a single image,
  return the image without the enclosing paragraph"
  [i]
  (if
    (and (vector? i) (= :p (first i)) (= 3 (count i)) (= :img (get-in i [2 0])))
    (let [[_tag {:keys [src alt] :as attrs}] (peek i)]
      ;; override for README img link
      (if (= "<img src=\"dev/logo.svg\">" alt)
        [:a {:href "https://adorn.fabricate.site"}
         [:img {:src "/logo-transparent.svg"}]]
        (peek i)))
    i))

(comment
  (unparagraph-img [:p {}
                    [:img
                     {:src   "/logo-transparent.svg"
                      :alt   "<img src=\"dev/logo.svg\">"
                      :title nil}]]))




(def pages
  {"index.html"
   (md-page (md/parse-md (slurp "README.md")
                         {:lower-fns {:markdown/fenced-code-block convert-pre
                                      :markdown/task-list-item convert-task-list
                                      :p unparagraph-img}}))
   "performance.html"
   (md-page (md/parse-md (slurp "notes/performance.md")
                         {:lower-fns {:markdown/fenced-code-block convert-pre
                                      :markdown/task-list-item convert-task-list
                                      :p unparagraph-img}}))
   #_#_"demos.html"
     ;; look at how easy this is
     [c/doctype-html5
      [:html [:head [:title "Adorn: code formatting demos"] imports]
       [:body
        [:main
         (into [:article]
               (map site-fns/process-form
                    (adorn/clj->hiccup (slurp "dev/site/demos.clj"))))]]]]
   "API/adorn.html"
   [c/doctype-html5
    [:html [:head [:title "Adorn: Core API"] imports]
     [:body
      [:main
       [:article [:h1 [:code "site.fabricate.adorn"] " namespace"]
        [:p {:class "clojure-ns-doc"}
         (:doc (meta (find-ns 'site.fabricate.adorn)))]
        (into [:section {:class "api-docs"}]
              (map api-tools/document-ns-var
                   (ns-publics 'site.fabricate.adorn.forms)))
        [:section {:class "api-source"} [:h1 "Source code"]
         [:pre
          (into [:code {:class "language-clojure"}
                 (adorn/clj->hiccup (slurp
                                     "src/site/fabricate/adorn.cljc"))])]]]]]]]
   "API/adorn/forms.html"
   [c/doctype-html5
    [:html [:head [:title "Adorn: Forms API"] imports]
     [:body
      [:main
       [:article [:h1 [:code "site.fabricate.adorn.forms"] " namespace"]
        [:p {:class "clojure-ns-doc"}
         (:doc (meta (find-ns 'site.fabricate.adorn.forms)))]
        (into [:section {:class "api-docs"}]
              (map api-tools/document-ns-var
                   (ns-publics 'site.fabricate.adorn.forms)))
        [:section {:class "api-source"} [:h1 "Source code"]
         [:pre
          (into [:code {:class "language-clojure"}
                 (adorn/clj->hiccup
                  (slurp "src/site/fabricate/adorn/forms.cljc"))])]]]]]]]})


(defn build!
  [{:keys [pages] :or {pages pages} :as opts}]
  (doseq [[out-path page-data] pages]
    (spit (str out-dir out-path) (c/html page-data))))



(comment
  (md/parse-md (slurp "notes/performance.md"))
  (md/parse-body (slurp "README.md"))
  (build! {}))
