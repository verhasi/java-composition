/**
 * Pre-build assertion: verify that the source files contain concise syntax
 * that javac cannot compile without the preprocessor.
 *
 * We verify the source contains concise syntax markers. The actual proof that
 * javac rejects it is implicit: if the plugin didn't work, the build would fail.
 * We also attempt compilation via Process to prove javac rejects it.
 */

def sourceFile = new File(basedir, "src/main/java/com/example/Demo.java")
assert sourceFile.exists() : "Source file must exist"

// Verify the source contains concise syntax
def source = sourceFile.text
assert source.contains("-> this.name") : "Source must contain -> concise syntax"
assert source.contains("= Math::max") : "Source must contain = method reference syntax"

// Attempt javac directly — must fail
def javacExe = System.getProperty("java.home") + "/bin/javac"
def proc = [javacExe, "--release", "21", sourceFile.absolutePath].execute()
proc.waitFor()
def exitCode = proc.exitValue()

assert exitCode != 0 : "Compilation of concise syntax must FAIL without preprocessor. " +
    "javac exit code was " + exitCode + " (expected non-zero). " +
    "This proves the source genuinely contains syntax javac cannot handle."

println "[prebuild] Verified: javac rejects concise method body syntax (exit code: " + exitCode + ")."
