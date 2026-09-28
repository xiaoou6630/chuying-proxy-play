#include "uci_bridge.h"

#include <chrono>
#include <cstring>
#include <iostream>

namespace chuying {

// ---------------------------------------------------------------------------
// InputBuffer: feeds queued lines to the engine's std::cin
// ---------------------------------------------------------------------------
class EngineBridge::InputBuffer : public std::streambuf {
public:
    void pushLine(const std::string& line) {
        {
            std::lock_guard<std::mutex> lk(m_);
            q_.push_back(line + "\n");
        }
        cv_.notify_all();
    }

    // Unblock readers: engine sees EOF once queue drains.
    void close() {
        {
            std::lock_guard<std::mutex> lk(m_);
            closed_ = true;
        }
        cv_.notify_all();
    }

protected:
    int_type underflow() override {
        std::unique_lock<std::mutex> lk(m_);
        for (;;) {
            if (pos_ < g_.size()) return traits_type::to_int_type(g_[pos_]);
            if (!q_.empty()) {
                g_ = std::move(q_.front());
                q_.pop_front();
                pos_ = 0;
                continue;
            }
            if (closed_) return traits_type::eof();
            cv_.wait(lk);
        }
    }

    int_type uflow() override {
        int_type c = underflow();
        if (!traits_type::eq_int_type(c, traits_type::eof())) ++pos_;
        return c;
    }

    std::streamsize xsgetn(char* s, std::streamsize n) override {
        std::streamsize got = 0;
        while (got < n) {
            int_type c = underflow();
            if (traits_type::eq_int_type(c, traits_type::eof())) break;
            s[got++] = traits_type::to_char_type(c);
            ++pos_;
        }
        return got;
    }

private:
    std::mutex m_;
    std::condition_variable cv_;
    std::deque<std::string> q_;
    std::string g_;
    size_t pos_ = 0;
    bool closed_ = false;
};

// ---------------------------------------------------------------------------
// OutputBuffer: captures engine's std::cout, splits into lines
// ---------------------------------------------------------------------------
class EngineBridge::OutputBuffer : public std::streambuf {
public:
    explicit OutputBuffer(EngineBridge* owner) : owner_(owner) {}

protected:
    int_type overflow(int_type c) override {
        if (traits_type::eq_int_type(c, traits_type::eof())) {
            flushLine();
            return traits_type::not_eof(c);
        }
        char ch = traits_type::to_char_type(c);
        if (ch == '\n') flushLine();
        else pending_ += ch;
        return c;
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        for (std::streamsize i = 0; i < n; ++i) {
            if (s[i] == '\n') flushLine();
            else pending_ += s[i];
        }
        return n;
    }

private:
    void flushLine() {
        std::string line;
        line.swap(pending_);
        while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
        if (!line.empty()) owner_->pushOutput(std::move(line));
    }

    EngineBridge* owner_;
    std::string pending_;
};

// ---------------------------------------------------------------------------
// EngineBridge
// ---------------------------------------------------------------------------
EngineBridge& EngineBridge::instance() {
    static EngineBridge inst;
    return inst;
}

// JVM shutdown destroys the function-local static. A std::thread that has
// finished but was never joined still counts as joinable -> its destructor
// calls std::terminate ("terminate called without an active exception").
EngineBridge::~EngineBridge() {
    if (worker_.joinable()) {
        if (engineExited_.load()) worker_.join(); // finished but unjoined: clean up
        else worker_.detach();                    // still running: process is dying anyway
    }
}

bool EngineBridge::start(const std::vector<std::string>& args) {
    if (running_.exchange(true)) return false;

    engineExited_.store(false);
    streamsRestored_.store(false);

    inBuf_ = new InputBuffer();
    outBuf_ = new OutputBuffer(this);

    oldIn_ = std::cin.rdbuf(inBuf_);
    oldOut_ = std::cout.rdbuf(outBuf_);
    // flush after every insertion so lines reach us immediately
    oldFlags_ = std::cout.flags();
    std::cout << std::unitbuf;

    try {
        worker_ = std::thread(&EngineBridge::engineThreadMain, this, args);
    } catch (...) {
        restoreStreams();
        running_.store(false);
        return false;
    }
    return true;
}

void EngineBridge::engineThreadMain(std::vector<std::string> args) {
    std::vector<char*> argv;
    argv.push_back(const_cast<char*>("chuying-engine"));
    for (auto& a : args) argv.push_back(const_cast<char*>(a.c_str()));

    engine_main(static_cast<int>(argv.size()), argv.data());

    engineExited_.store(true);
    outCv_.notify_all();
    restoreStreams();
}

void EngineBridge::restoreStreams() {
    bool expected = false;
    if (!streamsRestored_.compare_exchange_strong(expected, true)) return;
    std::cout.flags(oldFlags_);
    if (oldOut_) std::cout.rdbuf(oldOut_);
    if (oldIn_) std::cin.rdbuf(oldIn_);
}

bool EngineBridge::send(const std::string& line) {
    if (!running_.load() || engineExited_.load()) return false;
    inBuf_->pushLine(line);
    return true;
}

bool EngineBridge::readLine(int timeoutMs, std::string& line) {
    std::unique_lock<std::mutex> lk(outM_);
    outCv_.wait_for(lk, std::chrono::milliseconds(timeoutMs), [&] {
        return !outQ_.empty() || engineExited_.load();
    });
    if (outQ_.empty()) return false;
    line = std::move(outQ_.front());
    outQ_.pop_front();
    return true;
}

void EngineBridge::pushOutput(std::string&& line) {
    {
        std::lock_guard<std::mutex> lk(outM_);
        outQ_.push_back(std::move(line));
    }
    outCv_.notify_all();
}

void EngineBridge::stop() {
    if (!running_.load()) return;
    send("quit");
    for (int i = 0; i < 80 && !engineExited_.load(); ++i)
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    inBuf_->close();
    if (worker_.joinable()) {
        if (engineExited_.load()) worker_.join();
        else worker_.detach();
    }
    restoreStreams();
    running_.store(false);
}

} // namespace chuying
