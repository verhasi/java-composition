" Vim syntax add-on: Concise Method Bodies (JEP 8209434)
" Highlights the concise method-body markers `->` and `=` and their payload in
" method-declaration position, e.g.
"
"     public int size()            -> c.size();
"     public boolean isEmpty()     -> c.isEmpty();
"     static int max(int a, int b) = Math::max;
"
" Install: place at ~/.vim/after/syntax/java.vim (or ship via a plugin's
" after/syntax/ directory). It EXTENDS the standard Java syntax; it does not
" replace it. Standard `{ ... }` bodies are untouched.
"
" Distributed with java-composition. Apache License 2.0.

" Only add our rules once per buffer.
if exists('b:current_syntax_concise')
  finish
endif

" The marker must sit in method-body position: after the `)` that closes the
" parameter list and before the terminating `;`. Anchoring on a preceding `)`
" (with only whitespace between) is what distinguishes the `=` method-reference
" marker from an ordinary assignment operator — `foo() = ...` is not valid Java
" assignment, so there is no collision with real code.

" --- Expression form:  ) -> <expr> ;
" Match the whole `-> ... ;` span as a container, so the payload can be colored
" as a normal Java expression region and the marker as an operator.
syn region javaConciseArrowBody
      \ matchgroup=javaConciseMarker
      \ start=+)\@<=\s*\zs->+
      \ end=+;+
      \ keepend oneline
      \ contains=@javaConcisePayload

" --- Method-reference form:  ) = <ref> ;
syn region javaConciseRefBody
      \ matchgroup=javaConciseMarker
      \ start=+)\@<=\s*\zs=+
      \ end=+;+
      \ keepend oneline
      \ contains=@javaConcisePayload

" The payload between marker and `;` is ordinary Java. Reuse the standard Java
" token groups so the expression (identifiers, calls, strings, numbers, the `::`
" of a method reference, etc.) inherits the active theme's colors.
syn cluster javaConcisePayload contains=javaType,javaString,javaCharacter,javaNumber,javaBoolean,javaOperator,javaConstant,javaDocComment,javaComment,javaLineComment

" Color the markers as operators (theme-consistent). The payload groups above
" carry their own standard links; the marker gets the operator color.
hi def link javaConciseMarker Operator

let b:current_syntax_concise = 1
