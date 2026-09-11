(ns kekkai.cli.main-test
  "Argument handling. Small, and worth testing anyway: the one rule that
  matters is that ssh's arguments survive untouched, and that is exactly the
  kind of thing a refactor breaks silently."
  (:require [cljs.test :refer [deftest is testing]]
            [kekkai.cli.main :as main]
            [kekkai.cli.ssh :as ssh]))

(deftest usage-names-the-boundary
  (testing "the help text has to say who decides reachability, because the
            commonest wrong assumption is that the CLI can grant it"
    (is (re-find #"netmap decides reachability" main/usage))))

(deftest the-help-command-does-not-exit-nonzero
  (is (nil? (main/dispatch ["help"]))))

(deftest the-destination-goes-after-the-options-and-before-the-command
  (testing "ssh is positional — `ssh [options] destination [command]` — so the
            destination has exactly one correct place. The first version of this
            test asserted the destination went last, which is how the bug got
            written: `kekkai ssh judah … 'echo hi'` then produced
            `hostname contains invalid characters`, because ssh read the remote
            command as the host"
    (is (= ["-p" "22" "-A" "-o" "BatchMode=yes" "127.0.0.1" "uptime"]
           (ssh/ssh-argv 22 ["-A" "-o" "BatchMode=yes" "uptime"] nil))))
  (testing "options that take a value do not swallow the destination"
    (is (= ["-p" "22" "-o" "BatchMode=yes" "-i" "key" "judah@127.0.0.1" "ls"]
           (ssh/ssh-argv 22 ["-o" "BatchMode=yes" "-i" "key" "ls"] "judah"))))
  (testing "no command: the destination is last"
    (is (= ["-p" "2222" "127.0.0.1"] (ssh/ssh-argv 2222 [] nil)))
    (is (= ["-p" "2222" "judah@127.0.0.1"] (ssh/ssh-argv 2222 [] "judah")))
    (is (= ["-p" "2222" "-A" "judah@127.0.0.1"]
           (ssh/ssh-argv 2222 ["-A"] "judah"))))
  (testing "an explicit destination stands, and keeps its command"
    (is (= ["-p" "2222" "-A" "bob@127.0.0.1" "uptime"]
           (ssh/ssh-argv 2222 ["-A" "bob@127.0.0.1" "uptime"] "judah")))))
