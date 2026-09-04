# Editor Support — Vim / Neovim

Syntax highlighting for concise method bodies (JEP 8209434) in Vim and Neovim.
This is a lightweight **syntax add-on**: it extends the standard Java syntax to color
the concise markers `->` and `=` and their payload. It does not replace the built-in
Java syntax, and standard `{ ... }` bodies are untouched.

```java
public int size()            -> c.size();      // -> colored as an operator, c.size() as Java
public boolean isEmpty()     -> c.isEmpty();
static int max(int a, int b) = Math::max;      // = colored as an operator, Math::max as Java
```

## What it highlights

- The concise **markers** `->` and `=` in method-declaration position, colored as
  operators (inherits your theme's operator color).
- The **payload** expression after the marker, using standard Java token groups so it
  matches the rest of your code.

The `=` marker is only highlighted when it follows a method's `)` (method-body position),
so ordinary assignments (`int x = 5;`) are never affected.

## Install (plugin manager — recommended)

The add-on lives in the `editors/vim` subdirectory of this project, laid out as a valid
Vim/Neovim plugin root (`after/syntax/java.vim`). Point your plugin manager at that
subdirectory and it installs directly from Git — no manual copying, updates come with the
manager.

Releases are delivered through Bitbucket, so the install URL is the public Bitbucket
repository (`master` branch):

**vim-plug** (Vim or Neovim) — full URL (non-GitHub), `rtp` for the subdirectory:

```vim
Plug 'https://bitbucket.org/mocker-guru/java-composition.git', { 'rtp': 'editors/vim' }
```

**lazy.nvim** (Neovim) — full URL, then expose the subdirectory on the runtimepath:

```lua
{
  url = "https://bitbucket.org/mocker-guru/java-composition.git",
  lazy = false,
  init = function()
    vim.opt.rtp:append(vim.fn.stdpath("data") .. "/lazy/java-composition/editors/vim")
  end,
}
```

**packer.nvim** (Neovim):

```lua
use { "https://bitbucket.org/mocker-guru/java-composition.git", rtp = "editors/vim" }
```

**Native packages** (no manager, Vim 8+/Neovim) — clone and expose the subdir:

```sh
git clone https://bitbucket.org/mocker-guru/java-composition.git \
  ~/.vim/pack/plugins/start/java-composition
# then, in your vimrc:
#   set runtimepath+=~/.vim/pack/plugins/start/java-composition/editors/vim
```

After installing, open any `.java` file with concise bodies — the markers and payload are
highlighted.

> The common mechanism behind all of these is simply putting `editors/vim` on Vim's
> `runtimepath` (verified: with `runtimepath+=editors/vim`, the add-on's syntax items load
> and highlight). The exact option name differs per manager (`rtp` for vim-plug/packer, an
> `rtp:append` for lazy.nvim) because the plugin lives in a subdirectory rather than the repo
> root.
>
> **Availability:** the editor add-on ships with the release that carries the `editors/vim`
> directory. If your install finds nothing to highlight, you are likely on an older release
> that predates it — update to the latest.

## Install (manual copy)

If you prefer not to use a plugin manager, copy the single syntax file into your Vim
runtime's `after/syntax` directory so it extends the standard Java syntax:

```sh
mkdir -p ~/.vim/after/syntax
cp editors/vim/after/syntax/java.vim ~/.vim/after/syntax/java.vim
```

Open any `.java` file with concise bodies — the markers and payload are highlighted.

## Install (Neovim)

Neovim reads the same location under its config directory:

```sh
mkdir -p ~/.config/nvim/after/syntax
cp editors/vim/after/syntax/java.vim ~/.config/nvim/after/syntax/java.vim
```

(For Tree-sitter-based highlighting in Neovim, a Tree-sitter query variant is planned —
see `docs/plan-ide-support.md`, Phase A.)

## Method references

The `::` in a method reference (`Math::max`) is colored using the standard `javaMethodRef`
group, so it looks the same inside a concise body as it does anywhere else in your Java
code. Bare identifiers around it (`Math`, `max`) follow Vim's normal Java coloring — Vim's
standard syntax does not specially color method-reference identifiers, and this add-on stays
consistent with that.

## Automated test

A regression test asserts that the add-on's syntax items load correctly (via the real
`~/.vim/after/syntax/` auto-load path) and that the markers are anchored on a preceding `)`:

```sh
./editors/vim/test/run-tests.sh
```

It checks group definitions (deterministic headless) rather than rendered colors, guarding
against broken patterns, renamed groups, and the reload bug where a second load of the
standard `java.vim` could clear the add-on's items.

## Verify manually

Open the bundled example and confirm the markers/payload are colored:

```sh
vim editors/vim/sample-concise.java
```

Place the cursor on a `->` or `=` marker and run `:echo synIDattr(synID(line('.'), col('.'), 1), 'name')`
— it reports `javaConciseMarker`. On the payload it reports `javaConciseArrowBody` or
`javaConciseRefBody`. On an ordinary assignment `=` it reports the standard Java group
(never `javaConciseMarker`).
