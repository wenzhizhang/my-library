import { useNavigate } from 'react-router-dom';

import { useAuth } from '../AuthContext';
import { LIBRARY_PATH } from '../config';

/** Enter the guest session and go to the library. */
export default function useGuestLogin() {
  const { enterGuest } = useAuth();
  const navigate = useNavigate();

  return () => {
    enterGuest();
    navigate(`${LIBRARY_PATH}/books`);
  };
}
