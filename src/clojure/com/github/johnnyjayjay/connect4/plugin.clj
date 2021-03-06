(ns com.github.johnnyjayjay.connect4.plugin
  (:gen-class
    :name com.github.johnnyjayjay.connect4.ConnectFourPlugin
    :extends com.github.johnnyjayjay.connect4.ClojurePlugin)
  (:import (org.bukkit.plugin.java JavaPlugin)
           (com.github.johnnyjayjay.connect4 Connect4Command)))

(declare plugin)

(defn -onLoad [this]
  (alter-var-root #'plugin (constantly this)))

(defn -onEnable [^JavaPlugin this]
  (.. this (getCommand "connect4") (setExecutor (Connect4Command.)))
  (.. this (getLogger) (info "Clojure Plugin connect-four enabled!")))

