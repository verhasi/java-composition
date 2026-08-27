/**
 * Verify that non-Java files in source roots are copied to the generated output,
 * and Java files are preprocessed.
 */

def generatedBase = new File(basedir, "target/generated-sources/java-composition")
assert generatedBase.exists() : "Generated sources base directory must exist"

// Find the generated Java file
def generatedJava = null
generatedBase.eachFileRecurse { file ->
    if (file.name == "Greeter.java") {
        generatedJava = file
    }
}
assert generatedJava != null : "Generated Greeter.java must exist"
def generated = generatedJava.text
assert generated.contains("return this.name;") : "Concise syntax must be expanded"
assert !generated.contains("-> this.name") : "Concise syntax must not remain"

// Find the copied non-Java file
def copiedHtml = null
generatedBase.eachFileRecurse { file ->
    if (file.name == "package.html") {
        copiedHtml = file
    }
}
assert copiedHtml != null : "Non-Java file (package.html) must be copied to generated output"
assert copiedHtml.text.contains("example classes") : "Content of non-Java file must be preserved"

// Verify compilation succeeded
def classFile = new File(basedir, "target/classes/com/example/Greeter.class")
assert classFile.exists() : "Greeter.class must exist (compilation succeeded)"

println "[verify] Non-Java files test passed: Java preprocessed, non-Java files copied."
