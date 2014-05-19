package net.onrc.onos.core.util;

import org.hamcrest.Description;
import org.hamcrest.StringDescription;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;


/**
 * Hamcrest style class for verifying that a class follows the
 * accepted rules for utility classes.
 *
 * The rules that are enforced for utility classes:
 *    - the class must be declared final
 *    - the class must have only one constructor
 *    - the constructor must be private and inaccessible to callers
 *    - the class must have only static methods
 */

public class UtilityClassChecker {

    private String failureReason = "";

    /**
     * Method to determine if a given class is a properly specified
     * utility class.  In addition to checking that the class meets the criteria
     * for utility classes, an object of the class type is allocated to force
     * test code coverage onto the class constructor.
     *
     * @param clazz the class to check
     * @return true if the given class is a properly specified utility class.
     */
    private boolean isProperlyDefinedUtilityClass(Class<?> clazz) {
        // class must be declared final
        if (!Modifier.isFinal(clazz.getModifiers())) {
            failureReason = "a class that is not final";
            return false;
        }

        // class must have only one constructor
        final Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length != 1) {
            failureReason = "a class with more than one constructor";
            return false;
        }

        //  constructor must not be accessible outside of the class
        final Constructor<?> constructor = constructors[0];
        if (constructor.isAccessible()) {
            failureReason = "a class with an accessible default constructor";
            return false;
        }

        // constructor must be private
        if (!Modifier.isPrivate(constructor.getModifiers())) {
            failureReason = "a class with a default constructor that is not private";
            return false;
        }

        // class must have only static methods
        for (final Method method : clazz.getMethods()) {
            if (method.getDeclaringClass().equals(clazz)) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    failureReason = "a class with one or more non-static methods";
                    return false;
                }
            }

        }

        // Trigger an allocation of the object to force the hidden
        // constructor call
        try {
            constructor.setAccessible(true);
            constructor.newInstance();
            constructor.setAccessible(false);
        } catch (InstantiationException|IllegalAccessException|
                 InvocationTargetException error) {
            failureReason = error.getCause() + " when calling constructor";
            return false;
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
        description.appendText("a properly defined utility class");
    }

    /**
     * Assert that the given class adheres to the utility class rules.
     *
     * @param clazz the class to check
     *
     * @throws java.lang.AssertionError if the class is not a valid
     *         utility class
     */
    public static void assertThatClassIsUtility(Class<?> clazz) {
        final UtilityClassChecker checker = new UtilityClassChecker();
        if (!checker.isProperlyDefinedUtilityClass(clazz)) {
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
