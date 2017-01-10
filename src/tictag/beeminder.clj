(ns tictag.beeminder
  (:require [org.httpkit.client :as http]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [tictag.config :as config :refer [config]]
            [clojure.data :refer [diff]]))

(defn goal-url [user goal]
  (format "https://www.beeminder.com/api/v1/users/%s/goals/%s.json" user goal))

(defn datapoints-url [user goal]
  (format "https://www.beeminder.com/api/v1/users/%s/goals/%s/datapoints.json" user goal))

(defn datapoints [creds user goal]
  (:datapoints
   (cheshire/parse-string
    (:body
     @(http/request {:url         (goal-url user goal)
                     :method      :get
                     :query-params {:auth_token (:auth-token creds)
                                    :datapoints true}}))
    true)))

(defn update-datapoint! [creds user goal datapoint]
  (http/request {:url (format "https://www.beeminder.com/api/v1/users/%s/goals/%s/datapoints/%s.json"
                              user goal (:id datapoint))
                 :method :put
                 :query-params {:auth_token (:auth-token creds)
                                :value (:value datapoint)}}))

(defn create-datapoint! [creds user goal datapoint]
  (http/request {:url (format "https://www.beeminder.com/api/v1/users/%s/goals/%s/datapoints.json"
                              user goal)
                 :method :post
                 :query-params {:auth_token (:auth-token creds)
                                :value (:value datapoint)
                                :daystamp (:daystamp datapoint)}}))

(defn save-datapoint! [creds user goal datapoint]
  (if (:id datapoint)
    (update-datapoint! creds user goal datapoint)
    (create-datapoint! creds user goal datapoint)))

(defn delete-datapoint! [creds user goal datapoint]
  (when (:id datapoint)
    (http/request {:url (format "https://www.beeminder.com/api/v1/users/%s/goals/%s/datapoints/%s.json"
                                user goal (:id datapoint))
                   :method :delete
                   :query-params {:auth_token (:auth-token creds)}})))

(defn days-matching-pred [pred? rows]
  (->> rows
       (filter pred?)
       (map :local-day)
       (frequencies)))

(defn sync! [creds user goals rows]
  (when (and creds user (seq goals))
    (doseq [[goal pred?] goals]
      (let [days (days-matching-pred pred? rows)
            existing-datapoints (datapoints creds user goal)
            existing-map        (group-by :daystamp existing-datapoints)
            ;; we save anything that exists in our new datapoints
            to-save             (filter :value
                                        (for [[daystamp value] days
                                              :let             [{id :id old-value :value}
                                                                (first
                                                                 (existing-map daystamp))]]
                                          {:id       id
                                           :daystamp daystamp
                                           :value    (when (or (not old-value)
                                                               (not= (int old-value) value))
                                                       value)}))
            ;; we delete anything that
            ;; a) has a day that doesn't appear in our new datapoints, or
            ;; b) is a second datapoint for a day that appears in our new datapoints
            to-delete           (concat
                                 (remove (fn [{:keys [daystamp]}]
                                           (days daystamp))
                                         existing-datapoints)
                                 (flatten
                                  (remove nil?
                                          (map rest (vals existing-map)))))
            save-futures        (doall (map #(save-datapoint! creds user goal %) to-save))
            delete-futures      (doall (map #(delete-datapoint! creds user goal %) to-delete))]
        (doseq [resp (concat save-futures delete-futures)]
          (timbre/debugf "result %s %s: %s"
                         (-> @resp :opts :url)
                         (-> @resp :opts :method)
                         (:status @resp)))))))
