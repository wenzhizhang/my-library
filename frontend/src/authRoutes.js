/**
 * Auth routes live here so the router and the assistant's visibility gate
 * cannot drift apart.
 */
export const AUTH_ROUTES = ['login', 'register'];

export function isAuthPath(pathname = '') {
  return AUTH_ROUTES.some((route) => new RegExp(`/${route}/?$`).test(pathname));
}
