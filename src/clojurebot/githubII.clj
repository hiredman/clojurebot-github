(ns clojurebot.githubII
  (:require [clojurebot.feed]
            [clj-http.client :as client]
            [org.danlarkin.json :as json]
            [hiredman.utilities :as utils])
  (:import (javax.net.ssl X509TrustManager
                          TrustManager
                          SSLContext
                          HttpsURLConnection)))

(defonce fix-ssl
  (delay
   (let [mangers (into-array TrustManager
                             [(reify X509TrustManager
                                (getAcceptedIssuers [_] nil)
                                (checkClientTrusted [_ certs type])
                                (checkServerTrusted [_ certs type]))])
         ssl-cxt (doto (SSLContext/getInstance "SSL")
                   (.init nil mangers (java.security.SecureRandom.)))]
     (HttpsURLConnection/setDefaultSSLSocketFactory
      (.getSocketFactory ssl-cxt)))))

(def url "https://github.com/%s/commits/%s.atom?login=%s&token=%s")

(defn commits [repo branch username token regex]
  @fix-ssl
  (->> (clojurebot.feed/atom-pull* (format url repo branch username token))
       (filter (comp (partial re-find regex)
                     :title))
       (map :title)
       (take 5)
       (reduce #(str %1 %2 "\n") nil)))

(def pull-url "https://api.github.com/repos/%s/pulls")

(defn pull-requests [& projects]
  (->> (for [[repo username token] projects]
         (try
           (let [resp (client/get
                       (format pull-url repo)
                       {:basic-auth [username token]
                        :insecure? true})]
             (->> (json/decode-from-str (:body resp))
                  (map (fn [p]
                         (-> p
                             (update-in [:html_url]
                                        (fn [url] (utils/tinyurl url)))
                             (select-keys [:title :html_url])
                             (assoc :repo repo))))))
           (catch Exception e
             (clojure.tools.logging/info e repo username))))
       (apply concat)
       (reduce #(str %1 %2 "\n") nil)
       (str "Open pull requests:\n")))
