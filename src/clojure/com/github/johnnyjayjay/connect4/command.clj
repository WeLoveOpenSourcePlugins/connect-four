(ns com.github.johnnyjayjay.connect4.command
  (:gen-class
    :name com.github.johnnyjayjay.connect4.Connect4Command
    :implements [org.bukkit.command.CommandExecutor])
  (:require [clojure.core.match :refer [match]]
            [clojure.string :refer [lower-case]]
            [clojure.core.async :refer [chan go-loop <! alt! pipe close!]]
            [com.github.johnnyjayjay.connect4.util :refer [runsync pipe-events! run]]
            [com.github.johnnyjayjay.connect4.game :as connect4]
            [com.github.johnnyjayjay.connect4.inventory :as inv]
            [com.github.johnnyjayjay.connect4.plugin :refer [plugin]])
  (:import (org.bukkit.command CommandSender)
           (org.bukkit.entity Player)
           (org.bukkit Bukkit)
           (java.util UUID)
           (org.bukkit.event.inventory InventoryCloseEvent InventoryEvent InventoryInteractEvent InventoryClickEvent)
           (org.bukkit.event Cancellable)))

(defonce requests (atom {}))

(def player->uuid (memfn ^Player getUniqueId))
(def player->name (memfn ^Player getName))

(def event->player (memfn ^InventoryInteractEvent getWhoClicked))
(def event->inventory (memfn ^InventoryEvent getClickedInventory))
(def event->slot (memfn ^InventoryClickEvent getSlot))

(defn cancel-event [^Cancellable event]
  (.setCancelled event true))

(defn end-game [close-chan click-chan players message]
  (close! close-chan)
  (close! click-chan)
  (runsync plugin
    (run! #(.sendMessage % message) players)))

(defn start-game [players]
  (let [player-uuids (set (map player->uuid players))
        player-names (map player->name players)
        game-inventory (inv/create-inventory)
        inv-filter-xf (filter (comp #{game-inventory} event->inventory))
        close-chan (chan 1 inv-filter-xf)
        click-chan (chan 1 (comp inv-filter-xf (run cancel-event)))]
    (swap! requests #(reduce dissoc % (concat player-uuids (filter (comp player-uuids %) (keys %)))))
    (pipe-events! plugin InventoryCloseEvent close-chan)
    (pipe-events! plugin InventoryClickEvent click-chan)
    (run! #(.openInventory % game-inventory) players)
    (go-loop [{[current] :cycle :as game} (-> player-names connect4/start-game inv/prepare-items)]
      (let [{:keys [state] :as assessment} (connect4/assess game)
            new-inv-content (inv/render game)]
        (runsync plugin
          (.setContents game-inventory new-inv-content))
        (case state
          :tied (end-game close-chan click-chan players "§aIt's a tie!")

          :won
          (let [{:keys [winner win-line]} assessment]
            (runsync plugin
              (.setContents game-inventory (inv/highlight-win-line game new-inv-content winner win-line)))
            (end-game close-chan click-chan players (str "§6" winner "§a wins the game!")))

          :continue
          (let [play-chan (chan 1 (comp (filter (comp #{current} player->name event->player))
                                        (map event->slot)
                                        (map inc)
                                        (filter (connect4/valid-columns game))))]
            (pipe click-chan play-chan)
            (alt!
              play-chan ([column]
                         (close! play-chan)
                         (recur (connect4/play game column)))
              close-chan (end-game close-chan click-chan players "§cThe game was aborted by one of the players."))))))))

(defn -onCommand [this ^CommandSender sender command label args]
  (if (instance? Player sender)
    (match (vec args)
      ["play" ^String name]
      (if-let [opponent (Bukkit/getPlayer name)]
        (do
          (swap! requests assoc (player->uuid opponent) (player->uuid sender))
          (.sendMessage opponent (str "§6" (player->name sender) "§a invited you to play connect four."))
          (.sendMessage sender (str "§aA request to play has been sent to §6" name "§a.")))
        (.sendMessage sender (str "§cThe player §6" name "§c is not online.")))

      ["accept"]
      (if-let [^UUID opponent-id (@requests (player->uuid sender))]
        (if-let [opponent (Bukkit/getPlayer opponent-id)]
          (start-game [opponent sender])
          (.sendMessage sender "§cThe player who sent you the request is not online anymore."))
        (.sendMessage sender "§cThere is no pending request to accept."))

      :else
      (.sendMessage sender "§cPlease use §a/connect4 play [player]§c to send a play request to another player or §a/connect4 accept§c to accept a pending request."))
    (.sendMessage sender "§cThis command is only executable by players."))
  true)

