(ns kekkai.cli.main
  "Argument parsing and dispatch.

  Hand-rolled rather than pulled from a library because the surface is small
  and the alternative is a dependency this repository would carry for one
  function. The rule it follows: **everything after the subcommand's own
  arguments is passed through untouched**, which is what lets `kekkai ssh judah
  -A -L 8080:localhost:80` mean what an ssh user expects."
  (:require [clojure.string :as str]
            [kekkai.cli.commands :as commands]
            [kekkai.cli.publish :as publish]
            [kekkai.cli.ssh :as ssh]))

(def usage
  "kekkai — a zero-trust mesh you can drive from a shell

USAGE
  kekkai ssh <node> [--user U] [ssh args…]
                                    open a session over the overlay
  kekkai up [--forward L:NODE:R]… [--verbose]
                                    run this node; keep forwards open
  kekkai status                     peers, sessions, paths
  kekkai relay --tailnet T --version N
                                    run a relay (put it where peers can reach it)

  kekkai keygen [--node-id ID] [--out FILE]
  kekkai keygen authority [--out FILE]
  kekkai netmap publish --plane FILE --authority FILE [--out DIR] [--node ID]
  kekkai netmap verify <file> --authority SPKI_B64

COMMON OPTIONS
  --config FILE     node config (default ./kekkai-node.edn, then ~/.kekkai/node.edn)
  --port N          remote port for ssh (default 22)

The netmap decides reachability. Nothing here can grant what it does not.")

(defn- flag
  "-> the value after `name`, or nil. Flags are removed from `args` by
   `strip-flags`; both walk the same list so they cannot disagree."
  [args name]
  (second (drop-while #(not= name %) args)))

(defn- flag? [args name] (boolean (some #{name} args)))

(defn- collect
  "Every value following each occurrence of `name` — for repeatable flags."
  [args name]
  (loop [xs args out []]
    (if-let [rest* (seq (drop-while #(not= name %) xs))]
      (recur (drop 2 rest*) (conj out (second rest*)))
      out)))

(defn- strip-flags
  "Positional arguments only: drop every `--flag value` pair and bare `--flag`."
  [args flags-with-values]
  (loop [xs args out []]
    (if (empty? xs)
      out
      (let [x (first xs)]
        (cond
          (contains? flags-with-values x) (recur (drop 2 xs) out)
          (str/starts-with? (str x) "--") (recur (rest xs) out)
          :else (recur (rest xs) (conj out x)))))))

(def value-flags
  #{"--config" "--port" "--out" "--node-id" "--node" "--plane" "--authority"
    "--forward" "--tailnet" "--version" "--host" "--user"})

(defn- die [msg code]
  (println (str "kekkai: " msg))
  (.exit js/process code))

(defn dispatch [args]
  (let [[cmd & rest*] args
        positional (strip-flags (vec rest*) value-flags)]
    (case cmd
      ("ssh")
      (let [node (first positional)]
        (when-not node (die "kekkai ssh needs a node id" 64))
        ;; Everything the user typed after the node id goes to ssh verbatim —
        ;; including flags this CLI also happens to understand. `kekkai ssh` is
        ;; a launcher, not a parser of other people's arguments.
        (let [after (drop-while #(not= node %) rest*)]
          (ssh/run {:config-file (flag rest* "--config")
                    :node node
                    :port (some-> (flag rest* "--port") parse-long)
                    :login (flag rest* "--user")
                    :ssh-args (vec (remove #{"--user" (flag rest* "--user")}
                                           (rest after)))})))

      ("up")
      (commands/up {:config-file (flag rest* "--config")
                    :forwards (collect rest* "--forward")
                    :verbose? (flag? rest* "--verbose")})

      ("status")
      (commands/status {:config-file (flag rest* "--config")})

      ("relay")
      (let [tailnet (flag rest* "--tailnet")
            version (some-> (flag rest* "--version") parse-long)]
        (when-not (and tailnet version)
          (die (str "kekkai relay needs --tailnet and --version: the relay "
                    "handshake is bound to the same prologue the netmap "
                    "defines, so a mismatch has to fail loudly") 64))
        (commands/relay {:port (some-> (flag rest* "--port") parse-long)
                         :host (flag rest* "--host")
                         :static-file (or (flag rest* "--config")
                                          "kekkai-node.edn")
                         :tailnet tailnet :version version}))

      ("keygen")
      (commands/keygen {:kind (if (= "authority" (first positional))
                                :authority :node)
                        :node-id (flag rest* "--node-id")
                        :out (flag rest* "--out")})

      ("netmap")
      (case (first positional)
        "publish"
        (let [plane (flag rest* "--plane")
              authority (flag rest* "--authority")]
          (when-not (and plane authority)
            (die "kekkai netmap publish needs --plane and --authority" 64))
          (publish/run {:plane-file plane
                        :authority-file authority
                        :out-dir (or (flag rest* "--out") ".")
                        :only (flag rest* "--node")}))

        "verify"
        (let [file (second positional)
              authority (flag rest* "--authority")]
          (when-not file (die "kekkai netmap verify needs a file" 64))
          (when-not authority
            (die (str "kekkai netmap verify needs --authority <spki-b64>. It is "
                      "not read from the envelope on purpose: an envelope "
                      "carrying the only copy of its signer's key would "
                      "authenticate itself.") 64))
          (commands/verify {:netmap-file file :authority authority}))

        (die "kekkai netmap: expected `publish` or `verify`" 64))

      ("help" "--help" "-h" nil) (println usage)

      (die (str "unknown command '" cmd "'\n\n" usage) 64))))

(defn -main [& args]
  (try
    (dispatch (vec args))
    (catch :default e
      (println (str "kekkai: " (or (ex-message e) (str e))))
      (.exit js/process 70))))
