/**
 * Joins class names, dropping anything falsy.
 *
 * Small on purpose. A full `clsx` + `tailwind-merge` setup is worth adding once
 * components actually need to override each other's utility classes; until then it
 * would be a dependency doing the work of four lines.
 */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}
