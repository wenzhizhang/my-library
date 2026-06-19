const React = require('react');

const mockNavigate = jest.fn();

const MemoryRouter = ({ children }) => React.createElement(React.Fragment, null, children);
const Link = ({ to, children, ...rest }) => React.createElement('a', { href: to, ...rest }, children);
const useNavigate = () => mockNavigate;
const useParams = () => ({});
const useLocation = () => ({ pathname: '/', search: '', hash: '' });
const useSearchParams = () => [new URLSearchParams(), jest.fn()];
const Navigate = () => null;
const Routes = ({ children }) => React.createElement(React.Fragment, null, children);
const Route = ({ element }) => element || null;
const BrowserRouter = ({ children }) => React.createElement(React.Fragment, null, children);

module.exports = {
  mockNavigate,
  MemoryRouter,
  Link,
  useNavigate,
  useParams,
  useLocation,
  useSearchParams,
  Navigate,
  Routes,
  Route,
  BrowserRouter,
};
