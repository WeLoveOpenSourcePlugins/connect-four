(ns com.github.johnnyjayjay.connect4.util
  "Utility macros and functions."
  (:import (org.bukkit Bukkit)
           (org.bukkit.plugin Plugin EventExecutor)
           (org.bukkit.event Listener EventPriority HandlerList)
           (org.bukkit.scheduler BukkitScheduler))
  (:require [clojure.core.async :refer [chan put! close!]]))

(defmacro cond-doto
  "`cond-doto` is to `doto` what `cond->` is to `->`."
  [x & clauses]
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

(def run-task (memfn ^BukkitScheduler runTask ^Plugin plugin ^Runnable runnable))

(defmacro runsync
  "Executes the given body on the server thread and returns a channel that receives the result when it's done."
  [plugin & body]
  `(let [result-channel# (chan)]
     (run-task
       (Bukkit/getScheduler)
       ~plugin
       (fn []
         (if-let [result# (do ~@body)]
           (put! result-channel# result#)
           (close! result-channel#))))
     result-channel#))

(def listener-stub (reify Listener))

(defn event-executor
  "Returns an `EventExecutor` that puts events of the given type on the given channel.

  Unregisters the listener in question when the channel is closed."
  [event-type channel]
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