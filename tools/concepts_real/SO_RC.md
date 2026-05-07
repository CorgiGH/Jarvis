# SO_RC — real concept catalog

## Sistem UNIX — multitasking, multiutilizator
UNIX is a multitasking, multiuser OS structured as hardware → kernel → user-level applications; the kernel allocates CPU time and memory to processes through system calls (source: rc_lab1).

## Structura unui sistem Unix
Three-level architecture: hardware (network, HDD, memory, CPU), kernel (TCP/IP, File System Manager, Process Manager), user level (applications, shell) (source: rc_lab1).

## Kernel
The "command center" of the OS: schedules processes, allocates memory, manages files and inter-process communication via UNIX system calls (source: rc_lab1).

## Shell
A CLI command interpreter that acts as the user-kernel interface; behaves like a programming language (variables, control flow, scripts) (source: rc_lab1).

## Comenzi Bash externe vs interne
Externe (cat, rm) live in /bin or /usr/bin and require fork+exec; interne (cd, source, fg) are built into the shell — faster, no PATH lookup (source: rc_lab1).

## Execuția comenzilor: secvențială, paralelă, condiționată, foreground, background
`cmd1; cmd2` (sequential), `cmd1 | cmd2` (parallel via pipe), `cmd1 && cmd2` / `||` (conditional); `&` suffix runs in background, otherwise blocks the shell (source: rc_lab1).

## Comenzi de control fundal — jobs, fg, bg
`jobs` lists background processes, `fg %n` brings one to foreground, `bg %n` resumes a suspended process in background; suspend with Ctrl+Z (source: rc_lab1).

## Structura de directoare standard Unix
`/`, `/dev` (devices), `/etc` (config), `/usr`, `/lib`, `/boot` (kernel image), `/tmp`, `/home`, `/mnt`, `/var` (logs), `/proc` (virtual fs of running processes) (source: rc_lab1).

## Wildcards / metacaractere
`*` (0+ chars), `?` (single char), `[chars]` (range), `[^a]` (negation), `+`, `|`, `{n}` etc. for filename matching and regex (source: rc_lab1).

## Drepturi de acces — chmod
File/directory permissions: rwx for user/group/others; modified via `chmod` in octal (e.g., 750) or symbolic (`chmod +uW bau`) form (source: rc_lab1).

## Redirectarea I/O
`cmd < file` (stdin, fd 0), `cmd > file` (stdout, fd 1), `cmd 2> file` (stderr, fd 2); `>>` appends; pipe `|` chains stdout to next stdin (source: rc_lab1).

## Prelucrarea fișierelor — descriptori vs FILE*
Low-level: open/read/write/lseek/close primitives operate on integer file descriptors; high-level: fopen/fread/fputs/fseek/fclose use FILE* from stdio.h (source: rc_lab1).

## Prelucrarea directoarelor — dirent.h
opendir/readdir/closedir/rewinddir/seekdir/telldir/scandir + struct dirent; chdir, mkdir, rmdir, getcwd manipulate the working directory (source: rc_lab1).

## Proces și PID
Process = running instance of a program identified by a unique 5-digit PID; PPID = parent PID (source: rc_lab2).

## Stările unui proces
Start → Ready → Running → Waiting → Finished; Zombie (Z): completed but still in process table; Orphan: parent died, adopted by init process (source: rc_lab2).

## fork() — creating a child process
Returns PID > 0 in parent, 0 in child, -1 on error; child duplicates the address space of the parent (source: rc_lab2).

## exec() family
`execlp`, `execvp`, `execv`, `execl`, `execle` replace the current process image with a new program; combined with fork to spawn external programs (source: rc_lab2).

## wait() / waitpid() / WEXITSTATUS
Parent blocks until a child exits; retrieves child's exit status via the WEXITSTATUS macro (source: rc_lab2).

## Semnale (signals)
Software interrupts delivered by the OS to a process — can be triggered by hardware (SIGSEGV), OS (SIGCHLD), or user (SIGINT via Ctrl+C); each has a default action (source: rc_lab2).

## Semnale uzuale
SIGHUP (1), SIGINT (2 — Ctrl+C), SIGQUIT (3 — Ctrl+D), SIGFPE (8 — math error), SIGKILL (9 — uncatchable), SIGALRM (14 — timer), SIGTERM (15 — default kill) (source: rc_lab2).

## Primitive pentru semnale
`signal()` (legacy), `sigaction()` (preferred), `kill()` (send signal), `raise()` (send to self), `sigprocmask()` (block), `sigpending()`, `sigsuspend()`, `sigfillset()`, `sigdelset()` (source: rc_lab2).

## Pipe anonim
A unidirectional FIFO communication channel between related processes; `pipe(int fds[2])` returns fds[0] = read end, fds[1] = write end; reading blocks if nothing was written (source: rc_lab3).

## Pipe cu nume (FIFO)
Named pipe with a filesystem path; `mkfifo` creates it; usable by unrelated processes; persists until explicitly removed (source: rc_lab3).

## Duplicarea descriptorilor — dup, dup2
Duplicates a file descriptor entry in the process file table; both descriptors share the same underlying file (lock, cursor, flags); used to redirect stdin/stdout (source: rc_lab3).

## Implementarea "who | wc" via pipe + dup
Classic example: child reads stdin from pipe[0] via dup2; parent writes "who" output to pipe[1]; demonstrates shell pipeline mechanics (source: rc_lab3).

## socketpair()
`socketpair(domain, type, protocol, sv[2])` creates a connected pair of unnamed sockets — like a bidirectional pipe; commonly AF_UNIX + SOCK_STREAM (source: rc_lab4).

## Domenii de comunicare (AF_*)
AF_UNIX/AF_LOCAL = local IPC on same node; AF_INET = IPv4 internet; AF_INET6 = IPv6 (source: rc_lab4, rc_lab6).

## Tipuri de socket
SOCK_STREAM = reliable, ordered, byte-stream (TCP); SOCK_DGRAM = unreliable connectionless message-based (UDP); SOCK_SEQPACKET; SOCK_RAW = raw IP/ICMP (privileged) (source: rc_lab4, rc_lab6).

## Protocoale de transport
TCP (6, IPPROTO_TCP) — reliable; UDP (17) — datagram; SCTP (132) — stream control transmission protocol (source: rc_lab4).

## Modelul Client/Server iterativ TCP
Server: socket → bind → listen → accept (loop) → read → process → write → close. Client: socket → connect → write → read → close (source: rc_lab6).

## socket() primitive
`int socket(int domain, int type, int protocol)` — creates an endpoint; returns descriptor or -1 (source: rc_lab6).

## bind() primitive
`bind(sockfd, addr, addrlen)` assigns an address (struct sockaddr_in for IPv4: family, port, in_addr, sin_zero) to the socket (source: rc_lab6).

## inet_pton()
Converts a presentation-form IP string ("10.0.0.1") into a binary in_addr structure; companion is inet_ntop (source: rc_lab6).

## INADDR_ANY
Wildcard address bound to all local interfaces (typically 0.0.0.0); commonly used by servers (source: rc_lab6).

## listen() primitive
`listen(sockfd, backlog)` marks socket as passive, accepting connection requests; backlog = max queued pending connections before kernel refuses new ones (source: rc_lab6).

## accept() primitive
Blocks until a client connects, then returns a new socket descriptor for that client connection while the listening socket continues to accept (source: rc_lab6).

## connect() primitive
Client side: `connect(sockfd, serv_addr, addrlen)` initiates connection to server's listening socket (source: rc_lab6).

## htons / ntohs / htonl / ntohl
Byte-order conversion between host and network (big-endian); use `htons(port)` when filling sin_port (source: rc_lab6).

## Modelul Client/Server concurent TCP (fork)
After accept() the server forks; child handles the client (close the listening socket), parent loops back to accept; one process per connection (source: rc_lab7).

## Multiplexare I/O cu select()
`select(nfds, readfds, writefds, exceptfds, timeout)` monitors multiple descriptors; nfds = highest fd + 1; returns when at least one is ready (source: rc_lab9).

## fd_set și macrourile FD_*
FD_ZERO clear, FD_SET add, FD_CLR remove, FD_ISSET test; manipulate descriptor sets passed to select (source: rc_lab9).

## Server TCP cu select()
Single-process server multiplexes accept on listening socket and read on per-client sockets; handles many clients without forking (source: rc_lab9).

## Modelul Client/Server UDP/IP
Server: socket → bind → recvfrom (blocking) → sendto. Client: socket → sendto → recvfrom → close. No connect/accept needed (source: rc_lab10).

## recvfrom() primitive
`recvfrom(s, buf, len, flags, from, fromlen)` reads a datagram and populates `from` with sender's IP+port; flags may include MSG_PEEK, MSG_OOB, MSG_WAITALL (source: rc_lab10).

## sendto() primitive
`sendto(s, buf, len, flags, to, tolen)` sends a datagram to specified destination; flags include MSG_EOR, MSG_OOB (source: rc_lab10).

## Scheme de configurare socketpair / socket
Common configurations: AF_INET+SOCK_STREAM+0 → TCP; AF_INET+SOCK_DGRAM+0 → UDP; AF_UNIX+SOCK_STREAM → local TCP-like IPC (source: rc_lab4, rc_lab6).

## struct sockaddr_in (IPv4)
`{short sin_family; unsigned short sin_port; struct in_addr sin_addr; char sin_zero[8];}` — IPv4 address structure for bind/connect (source: rc_lab6).

## OS recap labs (rc_lab1..2)
Lab 1 reviews OS course material — Linux install on VM, Bash commands, file/directory primitives. Lab 2 covers process management and signal handling (source: rc_lab1, rc_lab2).

(image PDF, skipped — RC course PDFs rc_c1.pdf, rc_c2.pdf, rc_c3.pdf are password-protected/image-only and could not be extracted)
