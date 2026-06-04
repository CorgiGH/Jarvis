import java.io.*;
import java.nio.charset.StandardCharsets;

String py = "C:/Users/User/AppData/Local/Programs/Python/Python312/python.exe";
ProcessBuilder pb = new ProcessBuilder(py, "tools/nli_spike.py", "--stdin");
pb.directory(new File("C:/Users/User/jarvis-kotlin"));
pb.redirectError(ProcessBuilder.Redirect.DISCARD);   // drop python's HF warnings/progress
Process proc = pb.start();

String premise = "An algorithm is a well-ordered collection of unambiguous and effectively computable operations that when executed produces a result and halts in a finite amount of time.";
String hypothesis = "An algorithm halts in a finite amount of time.";

try (OutputStreamWriter w = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8)) {
    w.write(premise + "\n");
    w.write(hypothesis + "\n");
    w.flush();
}
String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
int code = proc.waitFor();
System.out.println("=== JVM ProcessBuilder -> py3.12 NLI ===");
System.out.println("exit=" + code + "  verdict_line=[" + out + "]");
System.out.println(out.startsWith("SUPPORTED") ? "BRIDGE GREEN" : "BRIDGE CHECK: " + out);
/exit
