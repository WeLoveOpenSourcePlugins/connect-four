(ns com.github.johnnyjayjay.connect4.util
  (:import (org.bukkit Bukkit)
           (org.bukkit.plugin Plugin EventExecutor)
           (org.bukkit.event Listener EventPriority HandlerList))
  (:require [clojure.core.async :refer [chan put!]]))

(defmacro cond-doto [x & clauses]
  (let [x-val (gensym "x")]
    `(let [~x-val ~x]
       (do
         ~@(map (fn [[test action]]
                  `(when ~test
                     ~(if (list? action)
                        `(~(first action) ~x-val ~@(rest action))
                        `(~action ~x-val))))
                (partition 2 clauses))
         ~x-val))))

(defmacro runsync [plugin & body]
  `(let [result-channel# (chan)]
     (.runTask
       (Bukkit/getScheduler)
       ^Plugin ~plugin
       ^Runnable (fn [] (put! result-channel# (do ~@body))))
     result-channel#))

(def listener-stub (reify Listener))

(defn event-executor [event-type channel]
  (reify EventExecutor
    (execute [this listener event]
      (when (= (type event) event-type)
        (when-not (put! channel event)
          (HandlerList/unregisterAll listener))))))

(defn pipe-events!
  "Registers a Bukkit listener that puts all events of the given type on the given core.async channel."
  [plugin type channel
   & {:keys [priority ignore-cancelled?]
      :or {priority (EventPriority/NORMAL) ignore-cancelled? false}}]
  (.registerEvent
    (Bukkit/getPluginManager)
    type
    listener-stub
    priority
    (event-executor type channel)
    plugin
    ignore-cancelled?))

(defn run
  "Returns a transducer that runs the given function for each input as a side effect."
  [proc]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input] (rf result (doto input proc))))))