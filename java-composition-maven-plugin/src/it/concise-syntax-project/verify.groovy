/**
 * Post-build verification: confirm the plugin transformed sources and compilation succeeded.
 */

// Find the generated source file (under the source-root-preserving path)
def generatedBase = new File(basedir, "target/generated-sources/java-composition")
assert generatedBase.exists() : "Generated sources base directory must exist"

// The source root path is preserved, so look for it
def generatedFile = null
generatedBase.eachFileRecurse { file ->
    if (file.name == "Demo.java") {
        generatedFile = file
    }
}
assert generatedFile != null : "Generated Demo.java must exist under generated-sources"

def generated = generatedFile.text

// Verify concise syntax is gone
assert !generated.contains("-> this.name") : "Generated source must not contain -> concise syntax"
assert !generated.contains("= Math::max") : "Generated source must not contain = method reference syntax"

// Verify standard Java was generated
assert generated.contains("return this.name;") : "getName should be expanded to return statement"
assert generated.contains("return items.size();") : "itemCount should be expanded"
assert generated.contains("System.out.println(this.name);") : "printName should be expanded"
assert generated.contains("return Math.max(a, b);") : "max should be expanded from method reference"

// Verify the standard method is preserved
assert generated.contains("return \"Demo{name=\"") : "toString should be preserved as-is"

// Verify compilation succeeded (class file exists)
def classFile = new File(basedir, "target/classes/com/example/Demo.class")
assert classFile.exists() : "Demo.class must exist (compilation must have succeeded)"

println "[verify] All checks passed: preprocessing transformed concise syntax, compilation succeeded."
