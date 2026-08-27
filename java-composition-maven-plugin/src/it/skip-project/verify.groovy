/**
 * Verify that when skip=true, no generated sources are created
 * and compilation uses the original source root.
 */

def generatedBase = new File(basedir, "target/generated-sources/java-composition")
assert !generatedBase.exists() : "Generated sources directory must NOT exist when skip=true"

// Compilation should still succeed (standard Java compiles from original source root)
def classFile = new File(basedir, "target/classes/com/example/Standard.class")
assert classFile.exists() : "Standard.class must exist (compilation from original source root)"

println "[verify] Skip test passed: no generated sources, compilation succeeded from original root."
