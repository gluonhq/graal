/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.espresso.polyglot.collections;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;

// Copied from java.util.AbstractList. The field modCount is removed
abstract class AbstractListWithoutField<E> extends AbstractCollection<E> implements List<E> {
    /**
     * Sole constructor. (For invocation by subclass constructors, typically implicit.)
     */
    protected AbstractListWithoutField() {
    }

    /**
     * Appends the specified element to the end of this list (optional operation).
     *
     * <p>
     * Lists that support this operation may place limitations on what elements may be added to this
     * list. In particular, some lists will refuse to add null elements, and others will impose
     * restrictions on the type of elements that may be added. List classes should clearly specify
     * in their documentation any restrictions on what elements may be added.
     *
     * @implSpec This implementation calls {@code add(size(), e)}.
     *
     *           <p>
     *           Note that this implementation throws an {@code UnsupportedOperationException}
     *           unless {@link #add(int, Object) add(int, E)} is overridden.
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws UnsupportedOperationException if the {@code add} operation is not supported by this
     *             list
     * @throws ClassCastException if the class of the specified element prevents it from being added
     *             to this list
     * @throws NullPointerException if the specified element is null and this list does not permit
     *             null elements
     * @throws IllegalArgumentException if some property of this element prevents it from being
     *             added to this list
     */
    @Override
    public boolean add(E e) {
        add(size(), e);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public abstract E get(int index);

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation always throws an {@code UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation always throws an {@code UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation always throws an {@code UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }

    // Search Operations

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation first gets a list iterator (with {@code listIterator()}). Then,
     *           it iterates over the list until the specified element is found or the end of the
     *           list is reached.
     *
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int indexOf(Object o) {
        ListIterator<E> it = listIterator();
        if (o == null) {
            while (it.hasNext()) {
                if (it.next() == null) {
                    return it.previousIndex();
                }
            }
        } else {
            while (it.hasNext()) {
                if (o.equals(it.next())) {
                    return it.previousIndex();
                }
            }
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation first gets a list iterator that points to the end of the list
     *           (with {@code listIterator(size())}). Then, it iterates backwards over the list
     *           until the specified element is found, or the beginning of the list is reached.
     *
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int lastIndexOf(Object o) {
        ListIterator<E> it = listIterator(size());
        if (o == null) {
            while (it.hasPrevious()) {
                if (it.previous() == null) {
                    return it.nextIndex();
                }
            }
        } else {
            while (it.hasPrevious()) {
                if (o.equals(it.previous())) {
                    return it.nextIndex();
                }
            }
        }
        return -1;
    }

    // Bulk Operations

    /**
     * Removes all of the elements from this list (optional operation). The list will be empty after
     * this call returns.
     *
     * @implSpec This implementation calls {@code removeRange(0, size())}.
     *
     *           <p>
     *           Note that this implementation throws an {@code UnsupportedOperationException}
     *           unless {@code remove(int
     * index)} or {@code removeRange(int fromIndex, int toIndex)} is overridden.
     *
     * @throws UnsupportedOperationException if the {@code clear} operation is not supported by this
     *             list
     */
    @Override
    public void clear() {
        removeRange(0, size());
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation gets an iterator over the specified collection and iterates
     *           over it, inserting the elements obtained from the iterator into this list at the
     *           appropriate position, one at a time, using {@code add(int, E)}. Many
     *           implementations will override this method for efficiency.
     *
     *           <p>
     *           Note that this implementation throws an {@code UnsupportedOperationException}
     *           unless {@link #add(int, Object) add(int, E)} is overridden.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        rangeCheckForAdd(index);
        boolean modified = false;
        int curIndex = index;
        for (E e : c) {
            add(curIndex++, e);
            modified = true;
        }
        return modified;
    }

    // Iterators

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * @implSpec This implementation returns a straightforward implementation of the iterator
     *           interface, relying on the backing list's {@code size()}, {@code get(int)}, and
     *           {@code remove(int)} methods.
     *
     *           <p>
     *           Note that the iterator returned by this method will throw an
     *           {@link UnsupportedOperationException} in response to its {@code remove} method
     *           unless the list's {@code remove(int)} method is overridden.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation returns {@code listIterator(0)}.
     *
     * @see #listIterator(int)
     */
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation returns a straightforward implementation of the
     *           {@code ListIterator} interface that extends the implementation of the
     *           {@code Iterator} interface returned by the {@code iterator()} method. The
     *           {@code ListIterator} implementation relies on the backing list's {@code get(int)},
     *           {@code set(int, E)}, {@code add(int, E)} and {@code remove(int)} methods.
     *
     *           <p>
     *           Note that the list iterator returned by this implementation will throw an
     *           {@link UnsupportedOperationException} in response to its {@code remove},
     *           {@code set} and {@code add} methods unless the list's {@code remove(int)},
     *           {@code set(int, E)}, and {@code add(int, E)} methods are overridden.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public ListIterator<E> listIterator(final int index) {
        rangeCheckForAdd(index);

        return new ListItr(index);
    }

    private class Itr implements Iterator<E> {
        /**
         * Index of element to be returned by subsequent call to next.
         */
        int cursor = 0;

        /**
         * Index of element returned by most recent call to next or previous. Reset to -1 if this
         * element is deleted by a call to remove.
         */
        int lastRet = -1;

        public boolean hasNext() {
            return cursor != size();
        }

        public E next() {
            try {
                int i = cursor;
                E next = get(i);
                lastRet = i;
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException(e);
            }
        }

        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }

            try {
                AbstractListWithoutField.this.remove(lastRet);
                if (lastRet < cursor) {
                    cursor--;
                }
                lastRet = -1;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }
    }

    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            cursor = index;
        }

        public boolean hasPrevious() {
            return cursor != 0;
        }

        public E previous() {
            try {
                int i = cursor - 1;
                E previous = get(i);
                lastRet = cursor = i;
                return previous;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException(e);
            }
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        public void set(E e) {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }

            try {
                AbstractListWithoutField.this.set(lastRet, e);
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void add(E e) {

            try {
                int i = cursor;
                AbstractListWithoutField.this.add(i, e);
                lastRet = -1;
                cursor = i + 1;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation returns a list that subclasses {@code AbstractList}. The
     *           subclass stores, in private fields, the size of the subList (which can change over
     *           its lifetime). There are two variants of the subclass, one of which implements
     *           {@code RandomAccess}. If this list implements {@code RandomAccess} the returned
     *           list will be an instance of the subclass that implements {@code RandomAccess}.
     *
     *           <p>
     *           The subclass's {@code set(int, E)}, {@code get(int)}, {@code add(int, E)},
     *           {@code remove(int)}, {@code addAll(int,
     * Collection)} and {@code removeRange(int, int)} methods all delegate to the corresponding
     *           methods on the backing abstract list, after bounds-checking the index and adjusting
     *           for the offset. The {@code addAll(Collection c)} method merely returns
     *           {@code addAll(size,
     * c)}    .
     *
     *           <p>
     *           The {@code listIterator(int)} method returns a "wrapper object" over a list
     *           iterator on the backing list, which is created with the corresponding method on the
     *           backing list. The {@code iterator} method merely returns {@code listIterator()},
     *           and the {@code size} method merely returns the subclass's {@code size} field.
     *
     * @throws IndexOutOfBoundsException if an endpoint index value is out of range
     *             {@code (fromIndex < 0 || toIndex > size)}
     * @throws IllegalArgumentException if the endpoint indices are out of order
     *             {@code (fromIndex > toIndex)}
     */
    public List<E> subList(int fromIndex, int toIndex) {
        subListRangeCheck(fromIndex, toIndex, size());
        return (this instanceof RandomAccess ? new RandomAccessSubList<>(this, fromIndex, toIndex) : new SubList<>(this, fromIndex, toIndex));
    }

    static void subListRangeCheck(int fromIndex, int toIndex, int size) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        }
        if (toIndex > size) {
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                            ") > toIndex(" + toIndex + ")");
        }
    }

    // Comparison and hashing

    /**
     * Compares the specified object with this list for equality. Returns {@code true} if and only
     * if the specified object is also a list, both lists have the same size, and all corresponding
     * pairs of elements in the two lists are <i>equal</i>. (Two elements {@code e1} and {@code e2}
     * are <i>equal</i> if {@code (e1==null ? e2==null :
     * e1.equals(e2))}.) In other words, two lists are defined to be equal if they contain the same
     * elements in the same order.
     *
     * @implSpec This implementation first checks if the specified object is this list. If so, it
     *           returns {@code true}; if not, it checks if the specified object is a list. If not,
     *           it returns {@code false}; if so, it iterates over both lists, comparing
     *           corresponding pairs of elements. If any comparison returns {@code false}, this
     *           method returns {@code false}. If either iterator runs out of elements before the
     *           other it returns {@code false} (as the lists are of unequal length); otherwise it
     *           returns {@code true} when the iterations complete.
     *
     * @param o the object to be compared for equality with this list
     * @return {@code true} if the specified object is equal to this list
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof List)) {
            return false;
        }

        ListIterator<E> e1 = listIterator();
        ListIterator<?> e2 = ((List<?>) o).listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            E o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1 == null ? o2 == null : o1.equals(o2))) {
                return false;
            }
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    /**
     * Returns the hash code value for this list.
     *
     * @implSpec This implementation uses exactly the code that is used to define the list hash
     *           function in the documentation for the {@link List#hashCode} method.
     *
     * @return the hash code value for this list
     */
    @Override
    public int hashCode() {
        int hashCode = 1;
        for (E e : this) {
            hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
        }
        return hashCode;
    }

    /**
     * Removes from this list all of the elements whose index is between {@code fromIndex},
     * inclusive, and {@code toIndex}, exclusive. Shifts any succeeding elements to the left
     * (reduces their index). This call shortens the list by {@code (toIndex - fromIndex)} elements.
     * (If {@code toIndex==fromIndex}, this operation has no effect.)
     *
     * <p>
     * This method is called by the {@code clear} operation on this list and its subLists.
     * Overriding this method to take advantage of the internals of the list implementation can
     * <i>substantially</i> improve the performance of the {@code clear} operation on this list and
     * its subLists.
     *
     * @implSpec This implementation gets a list iterator positioned before {@code fromIndex}, and
     *           repeatedly calls {@code ListIterator.next} followed by {@code ListIterator.remove}
     *           until the entire range has been removed. <b>Note: if {@code ListIterator.remove}
     *           requires linear time, this implementation requires quadratic time.</b>
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     */
    protected void removeRange(int fromIndex, int toIndex) {
        ListIterator<E> it = listIterator(fromIndex);
        for (int i = 0, n = toIndex - fromIndex; i < n; i++) {
            it.next();
            it.remove();
        }
    }

    private void rangeCheckForAdd(int index) {
        if (index < 0 || index > size()) {
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }
    }

    private String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + size();
    }

    private static class SubList<E> extends AbstractListWithoutField<E> {
        private final AbstractListWithoutField<E> root;
        private final SubList<E> parent;
        private final int offset;
        protected int size;

        /**
         * Constructs a sublist of an arbitrary AbstractList, which is not a SubList itself.
         */
        SubList(AbstractListWithoutField<E> root, int fromIndex, int toIndex) {
            this.root = root;
            this.parent = null;
            this.offset = fromIndex;
            this.size = toIndex - fromIndex;
        }

        /**
         * Constructs a sublist of another SubList.
         */
        protected SubList(SubList<E> parent, int fromIndex, int toIndex) {
            this.root = parent.root;
            this.parent = parent;
            this.offset = parent.offset + fromIndex;
            this.size = toIndex - fromIndex;
        }

        @Override
        public E set(int index, E element) {
            Objects.checkIndex(index, size);
            return root.set(offset + index, element);
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, size);
            return root.get(offset + index);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void add(int index, E element) {
            rangeCheckForAdd(index);
            root.add(offset + index, element);
            updateSize(1);
        }

        @Override
        public E remove(int index) {
            Objects.checkIndex(index, size);
            E result = root.remove(offset + index);
            updateSize(-1);
            return result;
        }

        @Override
        protected void removeRange(int fromIndex, int toIndex) {
            root.removeRange(offset + fromIndex, offset + toIndex);
            updateSize(fromIndex - toIndex);
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            return addAll(size, c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            rangeCheckForAdd(index);
            int cSize = c.size();
            if (cSize == 0) {
                return false;
            }
            root.addAll(offset + index, c);
            updateSize(cSize);
            return true;
        }

        @Override
        public Iterator<E> iterator() {
            return listIterator();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            rangeCheckForAdd(index);

            return new ListIterator<E>() {
                private final ListIterator<E> i = root.listIterator(offset + index);

                public boolean hasNext() {
                    return nextIndex() < size;
                }

                public E next() {
                    if (hasNext()) {
                        return i.next();
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                public boolean hasPrevious() {
                    return previousIndex() >= 0;
                }

                public E previous() {
                    if (hasPrevious()) {
                        return i.previous();
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                public int nextIndex() {
                    return i.nextIndex() - offset;
                }

                public int previousIndex() {
                    return i.previousIndex() - offset;
                }

                public void remove() {
                    i.remove();
                    updateSize(-1);
                }

                public void set(E e) {
                    i.set(e);
                }

                public void add(E e) {
                    i.add(e);
                    updateSize(1);
                }
            };
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return new SubList<>(this, fromIndex, toIndex);
        }

        private void rangeCheckForAdd(int index) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
            }
        }

        private String outOfBoundsMsg(int index) {
            return "Index: " + index + ", Size: " + size;
        }

        private void updateSize(int sizeChange) {
            SubList<E> slist = this;
            do {
                slist.size += sizeChange;
                slist = slist.parent;
            } while (slist != null);
        }
    }

    private static class RandomAccessSubList<E>
                    extends SubList<E> implements RandomAccess {

        /**
         * Constructs a sublist of an arbitrary AbstractList, which is not a RandomAccessSubList
         * itself.
         */
        RandomAccessSubList(AbstractListWithoutField<E> root,
                        int fromIndex, int toIndex) {
            super(root, fromIndex, toIndex);
        }

        /**
         * Constructs a sublist of another RandomAccessSubList.
         */
        RandomAccessSubList(RandomAccessSubList<E> parent,
                        int fromIndex, int toIndex) {
            super(parent, fromIndex, toIndex);
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return new RandomAccessSubList<>(this, fromIndex, toIndex);
        }
    }
}
