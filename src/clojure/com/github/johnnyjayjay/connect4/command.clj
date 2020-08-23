(ns com.github.johnnyjayjay.connect4.command
  (:gen-class
    :name com.github.johnnyjayjay.connect4.Connect4Command
    :implements [org.bukkit.command.CommandExecutor])
  (:require [clojure.core.match :refer [match]]
            [clojure.string :refer [lower-case]]
            [clojure.core.async :refer [chan go-loop <! alt! pipe close! take!]]
            [com.github.johnnyjayjay.connect4.util :refer [runsync pipe-events! run]]
            [com.github.johnnyjayjay.connect4.game :as connect4]
            [com.github.johnnyjayjay.connect4.inventory :as inv]
            [com.github.johnnyjayjay.connect4.plugin :refer [plugin]])
  (:import (org.bukkit.command CommandSender)
           (org.bukkit.entity Player HumanEntity)
           (org.bukkit Bukkit)
           (java.util UUID)
           (org.bukkit.event.inventory InventoryCloseEvent InventoryEvent InventoryInteractEvent InventoryClickEvent)
           (org.bukkit.event Cancellable)
           (org.bukkit.inventory Inventory ItemStack)))

(defonce requests (atom {}))

(def player->uuid (memfn ^Player getUniqueId))
(def player->name (memfn ^Player getName))

(def event->player (memfn ^InventoryInteractEvent getWhoClicked))
(def event->clicked-inventory (memfn ^InventoryClickEvent getClickedInventory))
(def event->inventory (memfn ^InventoryEvent getInventory))
(def event->slot (memfn ^InventoryClickEvent getSlot))

(defn cancel-event [^Cancellable event]
  (.setCancelled event true))

(def send-message (memfn ^CommandSender sendMessage ^String message))
(def open-inventory (memfn ^HumanEntity openInventory ^Inventory inventory))
(def set-contents (memfn ^Inventory setContents ^"[Lorg.bukkit.inventory.ItemStack;" items))

(defn end-game!
  "Ends the game by 'waiting' for all players to close the inventory, then closing the input channels.

  Also sends a message to the players."
  [close-chan click-chan players message]
  (let [final-close-chan (chan 1 (partition-all (count players)))]
    (pipe close-chan final-close-chan)
    (take! final-close-chan
           (fn [_]
             (close! close-chan)
             (close! click-chan))))
  (runsync plugin
    (run! #(send-message % message) players)))

(defn start-game
  "Starts a game for the given `Player`s."
  [players]
  (let [player-uuids (set (map player->uuid players))
        player-names (map player->name players)
        game-inventory (inv/create-inventory)
        close-chan (chan 1 (filter (comp #{game-inventory} event->inventory)))
        click-chan (chan 1 (comp (filter (comp #{game-inventory} event->clicked-inventory))
                                 (run cancel-event)))]
    (swap! requests #(reduce dissoc % (concat player-uuids (filter (comp player-uuids %) (keys %)))))
    (pipe-events! plugin InventoryCloseEvent close-chan)
    (pipe-events! plugin InventoryClickEvent click-chan)
    (run! #(open-inventory % game-inventory) players)
    (go-loop [{[current] :cycle :as game} (-> player-names connect4/make-game inv/prepare-items)]
      (let [{:keys [state] :as assessment} (connect4/assess game)
            new-inv-content (inv/render game)]
        (runsync plugin
          (set-contents game-inventory new-inv-content))
        (case state
          :tied (end-game! close-chan click-chan players "§aIt's a tie!")

          :won
          (let [{:keys [winner win-line]} assessment]
            (runsync plugin
              (set-contents game-inventory (inv/highlight-win-line! game new-inv-content winner win-line)))
            (end-game! close-chan click-chan players (str "§6" winner "§a wins the game!")))

          :continue
          (let [play-chan (chan 1 (comp (filter (comp #{current} player->name event->player))
                                        (map event->slot)
                                        (map dec)
                                        (filter (connect4/valid-columns game))))]
            (pipe click-chan play-chan)
            (alt!
              play-chan ([column]
                         (close! play-chan)
                         (recur (connect4/play game column)))
              close-chan (do
                           (close! play-chan)
                           (end-game! close-chan click-chan players "§cThe game was aborted by one of the players.")))))))))

(defn -onCommand [this ^CommandSender sender command label args]
  (if (instance? Player sender)
    (match (vec args)
      ["play" ^String name]
      (if-let [opponent (Bukkit/getPlayer name)]
        (do
          (swap! requests assoc (player->uuid opponent) (player->uuid sender))
          (send-message opponent (str "§6" (player->name sender) "§a invited you to play connect four."))
          (send-message sender (str "§aA request to play has been sent to §6" name "§a.")))
        (send-message sender (str "§cThe player §6" name "§c is not online.")))

      ["accept"]
      (if-let [^UUID opponent-id (@requests (player->uuid sender))]
        (if-let [opponent (Bukkit/getPlayer opponent-id)]
          (start-game [opponent sender])
          (send-message sender "§cThe player who sent you the request is not online anymore."))
        (send-message sender "§cThere is no pending request to accept."))

      :else
      (send-message sender "§cPlease use §a/connect4 play [player]§c to send a play request to another player or §a/connect4 accept§c to accept a pending request."))
    (send-message sender "§cThis command is only executable by players."))
  true)

