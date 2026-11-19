/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.shared.constraints;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;

/**
 * This class handles Loading Constraints as described in {@code JVMS - 5.3.4. Loading Constraints}.
 * <p>
 * To use this class, simply extend it and implement the abstract methods.
 * <p>
 * This implementation is parameterized to have flexibility on what values the constraints hold on
 * to. In particular, only the {@code Storage} type will be referenced from the constraints, and
 * thus not preventing {@code Loader} or {@code Klass} instances from being garbage collected.
 * Classes are recorded using their {@link #getUniqueClassId(Object) unique IDs}.
 * <p>
 * As an example, one may simply define {@code Storage} as a {@link java.lang.ref.WeakReference} of
 * the {@code Loader} type.
 * <p>
 * One may also call {@link #purge()} to remove recorded entries that are stale. A good time to do
 * so would be when it is known a class loader has been unloaded.
 *
 * @param <Loader> The type of the representation of class loaders used by the runtime.
 * @param <Klass> The type of the representation of classes used by the runtime.
 * @param <Storage> The type of the storage that should be used. It must be possible to uniquely
 *            obtain the {@code Loader} from the {@code Storage} and vice versa.
 */
public abstract class LoadingConstraintsShared<Loader, Storage, Klass> {
    private static final long NOT_YET_LOADED_ID = -1;

    /**
     * Loads the class {@code type} from loader {@code loader} if it is already loaded, {@code null}
     * otherwise. Does not trigger class loading if it has not yet been loaded.
     */
    public abstract Klass findLoadedClass(Symbol<Type> type, Loader loader);

    /**
     * Returns the {@code class loader} associated with the given {@code class}.
     */
    public abstract Loader getDefiningClassLoader(Klass cls);

    /**
     * Returns a unique {@code id} for the given {@code class}. The returned value must be positive.
     */
    public abstract long getUniqueClassId(Klass cls);

    /**
     * Obtain the storage value for the given class loader.
     * <p>
     * This method must be injective and idempotent, ie:
     * <ul>
     * <li>Different loaders must have different storages</li>
     * <li>Calling this method multiple times on the same loader must produce the same storage.</li>
     * </ul>
     */
    public abstract Storage toStorage(Loader loader);

    /**
     * Whether the given stored value represents the given loader.
     */
    public abstract boolean isSame(Storage storage, Loader loader);

    /**
     * Whether the provided {@code loader} is still alive.
     */
    public abstract boolean isAlive(Storage loader);

    /**
     * Checks that {@code loader1} and {@code loader2} resolve {@code type} as the same Klass
     * instance. {@code type} must not be an array type, nor a primitive type.
     */
    public final void checkConstraint(Symbol<Type> type, Loader loader1, Loader loader2) throws LoadingConstraintViolationException {
        assert !TypeSymbols.isArray(type) && !TypeSymbols.isPrimitive(type);
        Klass k1 = findLoadedClass(type, loader1);
        Klass k2 = findLoadedClass(type, loader2);
        checkOrAdd(type, getClassID(k1), getClassID(k2), loader1, loader2);
    }

    /**
     * Records that {@code loader} resolves {@code type} as {@code klass}. {@code type} must not be
     * an array type, nor a primitive type.
     */
    public final void recordConstraint(Symbol<Type> type, Klass k, Loader loader) throws LoadingConstraintViolationException {
        assert !TypeSymbols.isArray(type) && !TypeSymbols.isPrimitive(type);
        long klass = getClassID(k);
        ConstraintBucket<Loader, Storage> bucket = lookup(type);
        if (bucket == null) {
            Constraint<Loader, Storage> newConstraint = Constraint.create(this, klass, loader);
            bucket = new ConstraintBucket<>(newConstraint);
            ConstraintBucket<Loader, Storage> previous = pairings.putIfAbsent(type, bucket);
            if (previous != null) {
                assert previous == lookup(type);
                bucket = previous;
            }
        }
        synchronized (bucket) {
            Constraint<Loader, Storage> constraint = bucket.lookupLoader(this, loader);
            if (constraint == null) {
                constraint = bucket.lookupClass(klass);
                if (constraint == null) {
                    bucket.add(Constraint.create(this, klass, loader));
                } else {
                    constraint.add(this, loader);
                }
            } else {
                checkConstraint(klass, constraint);
            }
        }
    }

    /**
     * Removes from the constraints entries that are no longer {@link #isAlive(Object) alive}.
     */
    public final void purge() {
        for (ConstraintBucket<Loader, Storage> bucket : pairings.values()) {
            synchronized (bucket) {
                bucket.purge(this);
            }
        }
    }

    /**
     * Modifies the constraint registered for {@code klass}, so that it now points to
     * {@code newKlass}.
     * <p>
     * If {@code newKlass} is {@code null}, then the constraint is removed altogether.
     */
    public final void updateConstraint(Symbol<Type> type, Klass klass, Klass newKlass) {
        Loader loader = getDefiningClassLoader(klass);
        long oldKlassId = getClassID(klass);
        ConstraintBucket<Loader, Storage> bucket = lookup(type);
        if (bucket == null) {
            // Nothing to update
            return;
        }
        synchronized (bucket) {
            Constraint<Loader, Storage> origConstraint = bucket.lookupLoader(this, loader);

            // Ensure we are updating the constraint of the defining loader.
            assert bucket.lookupClass(oldKlassId) == null || origConstraint == bucket.lookupClass(oldKlassId);

            if (origConstraint != null) {
                if (newKlass == null) {
                    bucket.remove(origConstraint);
                } else {
                    long newKlassId = getClassID(newKlass);
                    origConstraint.swapKlass(newKlassId);
                }
            }
        }
    }

    // region implementation

    /**
     * Constraints are recorded per type instance. a constraint is represented as a Klass instance,
     * along with the loaders that load the given type as this klass. Most of the time, there will
     * be a single Klass instance around for a given type. but if multiple loaders have a different
     * instance of a Klass, say k1 and k2, with the same given type, then we need to record a
     * different constraint for k1 and k2.
     * <p>
     * As such, the entire set of constraints for a particular type, called a bucket, is a list of
     * constraints, one per Klass instance of this type. Each such constraint record all class
     * loaders that resolves the type as the recorded Klass instance.
     * <p>
     * To represent this, we use a map from types to buckets. Buckets are a doubly linked list. To
     * support concurrency, we use a ConcurrentHashMap. Failure to insert an item in the map due to
     * concurrency simply means someone was faster than us, we can therefore simply use the one that
     * is already present.
     * <p>
     * Once the bucket is obtained, we immediately synchronize on it. This prevents concurrency
     * problems as a whole, while allowing multiple threads to do constraint checking on different
     * types.
     */
    private final ConcurrentHashMap<Symbol<Type>, ConstraintBucket<Loader, Storage>> pairings = new ConcurrentHashMap<>();

    private void checkOrAdd(Symbol<Type> type, long k1, long k2, Loader loader1, Loader loader2) throws LoadingConstraintViolationException {
        if (exists(k1) && exists(k2) && k1 != k2) {
            throw new LoadingConstraintViolationException("Loading constraint violated !");
        }
        long klass = !exists(k1) ? k2 : k1;
        ConstraintBucket<Loader, Storage> bucket = lookup(type);
        if (bucket == null) {
            Constraint<Loader, Storage> newConstraint = Constraint.create(this, klass, loader1, loader2);
            bucket = new ConstraintBucket<>(newConstraint);
            ConstraintBucket<Loader, Storage> previous = pairings.putIfAbsent(type, bucket);
            if (previous != null) {
                bucket = previous;
            }
        }
        synchronized (bucket) {
            Constraint<Loader, Storage> c1 = bucket.lookupLoader(this, loader1);
            klass = checkConstraint(klass, c1);
            Constraint<Loader, Storage> c2 = bucket.lookupLoader(this, loader2);
            klass = checkConstraint(klass, c2);
            if (c1 == null && c2 == null) {
                bucket.add(Constraint.create(this, klass, loader1, loader2));
            } else if (c1 == c2) {
                /* Constraint already added */
            } else if (c1 == null) {
                c2.add(this, loader1);
                if (!exists(c2.klass)) {
                    c2.klass = klass;
                }
            } else if (c2 == null) {
                c1.add(this, loader2);
                if (!exists(c1.klass)) {
                    c1.klass = klass;
                }
            } else {
                mergeConstraints(bucket, c1, c2);
            }
        }
    }

    private ConstraintBucket<Loader, Storage> lookup(Symbol<Type> type) {
        return pairings.get(type);
    }

    private long checkConstraint(long klass, Constraint<Loader, Storage> c1) throws LoadingConstraintViolationException {
        if (c1 != null) {
            if (exists(c1.klass)) {
                if (exists(klass)) {
                    if (klass != c1.klass) {
                        throw new LoadingConstraintViolationException("New loading constraint violates an older one!");
                    }
                } else {
                    return c1.klass;
                }
            } else {
                c1.klass = klass;
            }
        }
        return klass;
    }

    private void mergeConstraints(ConstraintBucket<Loader, Storage> bucket, Constraint<Loader, Storage> c1, Constraint<Loader, Storage> c2) {
        assert Thread.holdsLock(bucket);
        Constraint<Loader, Storage> merge;
        Constraint<Loader, Storage> delete;
        if (c1.loaders.length < c2.loaders.length) {
            merge = c2;
            delete = c1;
        } else {
            merge = c1;
            delete = c2;
        }
        merge.merge(delete);
        bucket.remove(delete);
    }

    private long getClassID(Klass k) {
        return k == null ? NOT_YET_LOADED_ID : getUniqueClassId(k);
    }

    private static boolean exists(long klass) {
        return klass != NOT_YET_LOADED_ID;
    }

    /**
     * While simply holding a reference to the head of a list, these instances are being
     * synchronized on.
     */
    private static final class ConstraintBucket<Loader, Storage> {

        private Constraint<Loader, Storage> head;

        ConstraintBucket(Constraint<Loader, Storage> head) {
            synchronized (this) {
                add(head);
            }
        }

        Constraint<Loader, Storage> lookupLoader(LoadingConstraintsShared<Loader, Storage, ?> provider, Loader loader) {
            assert Thread.holdsLock(this);
            Constraint<Loader, Storage> curr = head;
            while (curr != null) {
                if (curr.contains(provider, loader)) {
                    return curr;
                }
                curr = curr.next;
            }
            return null;
        }

        Constraint<Loader, Storage> lookupClass(long klass) {
            assert Thread.holdsLock(this);
            Constraint<Loader, Storage> curr = head;
            while (curr != null) {
                if (curr.klass == klass) {
                    return curr;
                }
                curr = curr.next;
            }
            return null;
        }

        void add(Constraint<Loader, Storage> newConstraint) {
            assert Thread.holdsLock(this);
            newConstraint.next = head;
            newConstraint.prev = null;
            if (head != null) {
                head.prev = newConstraint;
            }
            head = newConstraint;
        }

        void remove(Constraint<Loader, Storage> toRemove) {
            assert Thread.holdsLock(this);
            Constraint<Loader, Storage> prev = toRemove.prev;
            Constraint<Loader, Storage> next = toRemove.next;
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }
            if (toRemove == head) {
                head = next;
            }
        }

        void purge(LoadingConstraintsShared<Loader, Storage, ?> provider) {
            assert Thread.holdsLock(this);
            Constraint<Loader, Storage> curr = head;
            while (curr != null) {
                curr.purge(provider);
                if (curr.size == 0) {
                    remove(curr);
                }
                curr = curr.next;
            }
        }
    }

    private static final class Constraint<Loader, Storage> {
        private long klass;

        /*
         * Most applications will only see the boot loader and the app class loader used.
         */
        private static final int DEFAULT_INITIAL_SIZE = 2;

        @SuppressWarnings("unchecked") //
        private Storage[] loaders = (Storage[]) new Object[DEFAULT_INITIAL_SIZE];
        private int size = 0;
        private int capacity = DEFAULT_INITIAL_SIZE;

        Constraint<Loader, Storage> prev;
        Constraint<Loader, Storage> next;

        Constraint(long k) {
            this.klass = k;
        }

        boolean contains(LoadingConstraintsShared<Loader, Storage, ?> provider, Loader loader) {
            for (int i = 0; i < size; i++) {
                if (provider.isSame(loaders[i], loader)) {
                    return true;
                }
            }
            return false;
        }

        boolean contains(Storage other) {
            for (int i = 0; i < size; i++) {
                if (loaders[i] == other) {
                    return true;
                }
            }
            return false;
        }

        void add(LoadingConstraintsShared<Loader, Storage, ?> provider, Loader loader) {
            assert !contains(provider, loader);
            if (size >= capacity) {
                loaders = Arrays.copyOf(loaders, capacity <<= 1);
            }
            loaders[size++] = Objects.requireNonNull(provider.toStorage(loader));
        }

        void add(Storage storage) {
            assert !contains(storage);
            if (size >= capacity) {
                loaders = Arrays.copyOf(loaders, capacity <<= 1);
            }
            loaders[size++] = Objects.requireNonNull(storage);
        }

        void merge(Constraint<Loader, Storage> other) {
            for (int i = 0; i < other.size; i++) {
                Storage stored = other.loaders[i];
                if (!contains(stored)) {
                    add(stored);
                }
            }
        }

        void purge(LoadingConstraintsShared<Loader, Storage, ?> provider) {
            int i = 0;
            while (i < size) {
                if (!provider.isAlive(loaders[i])) {
                    swap(i, --size);
                } else {
                    i++;
                }
            }
            if (size > 0 && // size == 0 gets reclaimed after
                            size < (capacity >> 1) &&
                            capacity > DEFAULT_INITIAL_SIZE // have 2 slots
            ) {
                int shift = 1;
                while (size < (capacity >> (shift + 1))) {
                    shift += 1;
                }
                int newCapacity = Math.max(DEFAULT_INITIAL_SIZE, capacity >> shift);
                loaders = Arrays.copyOf(loaders, newCapacity);
                capacity = newCapacity;
            }
        }

        void swapKlass(long newKlassId) {
            VarHandle.fullFence();
            this.klass = newKlassId;
            VarHandle.fullFence();
        }

        void swap(int i, int j) {
            Storage a = loaders[i];
            loaders[i] = Objects.requireNonNull(loaders[j]);
            loaders[j] = Objects.requireNonNull(a);
        }

        static <Loader, Storage> Constraint<Loader, Storage> create(LoadingConstraintsShared<Loader, Storage, ?> provider, long klass, Loader loader1, Loader loader2) {
            if (loader1 == loader2) {
                return create(provider, klass, loader1);
            }
            Constraint<Loader, Storage> constraint = new Constraint<>(klass);
            constraint.add(provider, loader1);
            constraint.add(provider, loader2);
            return constraint;
        }

        static <Loader, Storage> Constraint<Loader, Storage> create(LoadingConstraintsShared<Loader, Storage, ?> provider, long klass, Loader loader) {
            Constraint<Loader, Storage> constraint = new Constraint<>(klass);
            constraint.add(provider, loader);
            return constraint;
        }
    }
}
