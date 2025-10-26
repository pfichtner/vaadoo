package org.jmolecules.bytebuddy.vaadoo.fragments;

/**
 * The content of the code fragments are copied into the targets. So there are
 * some things that are prohibited in the code:
 * <li>other returns than the default return at the end of the code</li>
 * <li>calls to other methods (even calls to methods of the class itself)</li>
 * <li>field accesses</li>
 */
public interface Jsr380CodeFragment
		extends Jsr380CodeObjectsFragment, Jsr380CodeCharSequencesFragment, Jsr380CodeEmptyFragment,
		Jsr380CodeSizeFragment, Jsr380CodeBooleanFragment, Jsr380CodeNumberFragment, Jsr380CodeDateFragment {
}
