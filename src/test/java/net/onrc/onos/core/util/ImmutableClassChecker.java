package net.onrc.onos.core.util;

import org.hamcrest.Description;
import org.hamcrest.StringDescription;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Hamcrest style class for verifying that a class follows the
 * accepted rules for immutable classes.
 *
 * The rules that are enforced for immutable classes:
 *    - the class must be declared final
 *    - all data members of the class must be declared private and final
 *    - the class must not define any setter methods
 */

public class ImmutableClassChecker {

    private String failureReason = "";

    /**
     * Method to determine if a given class is a properly specified
     * immutable class.
     *
     * @param clazz the class to check
     * @return true if the given class is a properly specified immutable class.
     */
    private boolean isImmutableClass(Class<?> clazz) {
        // class must be declared final
        if (!Modifier.isFinal(clazz.getModifiers())) {
            failureReason = "a class that is not final";
            return false;
        }

        // class must have only final and private data members
        for (final Field field : clazz.getDeclaredFields()) {
            if (field.getName().startsWith("__cobertura")) {
                //  cobertura sticks these fields into classes - ignore them
                continue;
            }
            if (!Modifier.isFinal(field.getModifiers())) {
                failureReason = "a field named '" + field.getName() +
                                "' that is not final";
                return false;
            }
            if (!Modifier.isPrivate(field.getModifiers())) {
                failureReason = "a field named '" + field.getName() +
                                "' that is not private";
                return false;
            }
        }

        //  class must not define any setters
        for (final Method method : clazz.getMethods()) {
            if (method.getDeclaringClass().equals(clazz)) {
                if (method.getName().startsWith("set")) {
                    failureReason = "a class with a setter named '" + method.getName() + "'";
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Describe why an error was reported.  Uses Hamcrest style Description
     * interfaces.
     *
     * @param description the Description object to use for reporting the
     *                    mismatch
     */
    public void describeMismatch(Description description) {
        description.appendText(failureReason);
    }

    /**
     * Describe the source object that caused an error, using a Hamcrest
     * Matcher style interface.  In this case, it always returns
     * that we are looking for a properly defined utility class.
     *
     * @param description the Description object to use to report the "to"
     *                    object
     */
    public void describeTo(Description description) {
        description.appendText("a properly defined immutable class");
    }

    /**
     * Assert that the given class adheres to the utility class rules.
     *
     * @param clazz the class to check
     *
     * @throws java.lang.AssertionError if the class is not a valid
     *         utility class
     */
    public static void assertThatClassIsImmutable(Class<?> clazz) {
        final ImmutableClassChecker checker = new ImmutableClassChecker();
        if (!checker.isImmutableClass(clazz)) {
            final Description toDescription = new StringDescription();
            final Description mismatchDescription = new StringDescription();

            checker.describeTo(toDescription);
            checker.describeMismatch(mismatchDescription);
            final String reason =
                    "\n" +
                    "Expected: is \"" + toDescription.toString() + "\"\n" +
                    "    but : was \"" + mismatchDescription.toString() + "\"";

            throw new AssertionError(reason);
        }
    }
}
