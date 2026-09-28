// chuying native engine bridge - in-process UCI/pbrain stream redirection
// GPL-3.0-only. See THIRD_PARTY_LICENSES: links Stockfish/Pikafish/Rapfi (GPL-3.0).
//
// Design: the engine's main() is renamed to engine_main() at CI build time and
// runs on a dedicated thread inside this shared library. std::cin/std::cout of
// the engine are redirected to in-memory queues, so no OS process is spawned.
//
// NOTE: one shared library per engine (chuying_stockfish / chuying_pikafish /
// chuying_rapfi) because Stockfish and Pikafish share the same symbol
// namespace and cannot be linked into a single module.

#pragma once

#include <atomic>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <string>
#include <streambuf>
#include <thread>
#include <vector>

// Renamed entry point of the engine (patched by CI, see
// .github/scripts/build_native.sh). Global C++ linkage: the engine's main.cpp
// is plain global code, so the mangled name must match _Z11engine_mainiPPc.
extern int engine_main(int argc, char* argv[]);

namespace chuying {

class EngineBridge {
public:
    static EngineBridge& instance();

    EngineBridge(const EngineBridge&) = delete;
    EngineBridge& operator=(const EngineBridge&) = delete;

    // Launch the engine thread. args are passed to engine_main as argv[1..].
    // Returns false if an engine instance is already running.
    bool start(const std::vector<std::string>& args);

    // Queue one line of input for the engine. Returns false if not running.
    bool send(const std::string& line);

    // Pop one output line. Returns false on timeout (or dead engine + empty queue).
    bool readLine(int timeoutMs, std::string& line);

    // Ask the engine to quit ("quit"), wait, then mark stopped.
    void stop();

    bool alive() const { return !engineExited_.load(); }

private:
    EngineBridge() = default;

    void engineThreadMain(std::vector<std::string> args);
    void restoreStreams();
    void pushOutput(std::string&& line);

    // std::streambuf fed by Java-side send() -> engine's std::cin
    class InputBuffer;
    // std::streambuf capturing engine's std::cout -> line queue for Java
    class OutputBuffer;

    InputBuffer* inBuf_ = nullptr;
    OutputBuffer* outBuf_ = nullptr;
    std::streambuf* oldIn_ = nullptr;
    std::streambuf* oldOut_ = nullptr;
    std::ios_base::fmtflags oldFlags_ = std::ios_base::fmtflags(0);

    std::thread worker_;
    std::atomic<bool> running_{false};
    std::atomic<bool> engineExited_{false};
    std::atomic<bool> streamsRestored_{false};

    std::mutex outM_;
    std::condition_variable outCv_;
    std::deque<std::string> outQ_;
};

} // namespace chuying
