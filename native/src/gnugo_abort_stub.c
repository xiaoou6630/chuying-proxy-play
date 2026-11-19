/*
 * GNU Go 构建期工具的 abort 钩子桩（GPL-3.0-only，本文件）。
 *
 * 背景：
 *   GNU Go 3.8 里有几个**构建期小工具**会链接 libboard.a / libengine.a，
 *   而这些库里包含 printutils.c（abortgo 所在）。我们把 abortgo() 里的
 *   abort() 换成了 chuying_gnugo_abortgo_exit()，于是这些工具在链接时
 *   要求该符号存在：
 *     Undefined symbols for architecture arm64:
 *       "_chuying_gnugo_abortgo_exit", referenced from:
 *         _abortgo in libengine.a[27](printutils.c.o)
 *
 *   这些工具是：mkpat / mkeyes / mkmcpat / joseki / uncompress_fuseki /
 *   extract_fuseki / compress_fuseki。它们只在编译期生成模式库与定式库，
 *   运行时完全用不到，也不该链接 JNI 桥（那是 chuying_gnugo 共享库的事）。
 *
 * 为什么不能只靠 __attribute__((weak))：
 *   Linux/Windows 的 GNU ld 与 lld 能容忍 weak 未定义；
 *   **macOS 的 ld64 不行** —— 静态库成员里的未定义引用即使声明为 weak，
 *   仍然报 Undefined symbols（实测）。所以必须给这些工具提供一个真实实现。
 *
 * 这个桩的行为：直接 abort()。它只在构建期工具里被链接；那些工具如果
 * 真的触发 GNU Go 内部断言，本来就该失败退出（构建错误应当暴露），
 * 而运行时走的是 chuying_gnugo，那里用的是 gnugo_bridge.cpp 里的
 * longjmp 实现，绝不会到这儿来。
 */

#include <stdlib.h>

void chuying_gnugo_abortgo_exit(void)
{
    /* 构建期工具里触发断言 = GNU Go 源码或补丁有问题，直接失败即可。 */
    abort();
}
