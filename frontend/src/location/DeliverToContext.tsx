import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { SavedAddress } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DeliverToDialog } from './DeliverToDialog';

/** A place looked up from a postcode (place is null when the lookup service couldn't name it). */
export interface PostcodePlace {
  postcode: string;
  outcode: string;
  place: string | null;
  country: string | null;
}

/**
 * What this browser chose. userId says whose choice it is: a choice made while signed in only counts for that account
 * (so signing out never shows it), and one made while signed out carries over to an account with no saved addresses.
 */
interface Choice {
  userId: number | null;
  addressId?: number;
  postcode?: PostcodePlace;
}

interface DeliverTo {
  /** "Deliver to Samaun", or just "Deliver to". */
  heading: string;
  /** "Hillingdon UB8 3PH", or "Update location" when nothing is known. */
  place: string;
  known: boolean;
  /** The saved address to deliver to (checkout starts with it selected), if one is chosen or is the default. */
  address: SavedAddress | null;
  /** A postcode typed or found by location instead of a saved address. */
  postcode: PostcodePlace | null;
  addresses: SavedAddress[] | null;
  open: () => void;
  chooseAddress: (id: number) => void;
  choosePostcode: (p: PostcodePlace) => void;
  /** The address book changed: use its latest list. */
  syncAddresses: (list: SavedAddress[]) => void;
}

const KEY = 'pacific.deliverTo';
const Ctx = createContext<DeliverTo | null>(null);

function load(): Choice | null {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? 'null');
    return parsed && typeof parsed === 'object' ? (parsed as Choice) : null;
  } catch {
    return null;
  }
}

function save(choice: Choice) {
  try {
    localStorage.setItem(KEY, JSON.stringify(choice));
  } catch {
    /* storage unavailable: the choice lasts until the page is reloaded */
  }
}

const firstName = (name: string) => name.trim().split(/\s+/)[0] ?? name;
const placeOf = (p: PostcodePlace) => (p.place ? `${p.place} ${p.postcode}` : p.postcode);

/** "Deliver to" in the top bar: a saved address for signed-in customers, or a postcode (anyone). Kept in this browser. */
export function DeliverToProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const customerId = user?.role === 'CUSTOMER' ? user.id : null;
  const [choice, setChoice] = useState<Choice | null>(load);
  const [addresses, setAddresses] = useState<SavedAddress[] | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  const refresh = useCallback(() => {
    if (customerId == null) { setAddresses(null); return; }
    api<SavedAddress[]>('/me/addresses').then(setAddresses).catch(() => setAddresses([]));
  }, [customerId]);
  useEffect(refresh, [refresh]);

  const remember = useCallback((next: Choice) => { setChoice(next); save(next); }, []);
  const chooseAddress = useCallback((id: number) => remember({ userId: customerId, addressId: id }), [remember, customerId]);
  const choosePostcode = useCallback((p: PostcodePlace) => remember({ userId: customerId, postcode: p }), [remember, customerId]);
  const open = useCallback(() => { refresh(); setDialogOpen(true); }, [refresh]);

  const value = useMemo<DeliverTo>(() => {
    const mine = !choice ? null
      : (choice.userId ?? null) === customerId ? choice
      : choice.userId == null && addresses?.length === 0 ? choice
      : null;
    const chosen = mine?.addressId != null ? addresses?.find((a) => a.id === mine.addressId) ?? null : null;
    const postcode = !chosen && mine?.postcode ? mine.postcode : null;
    // Nothing chosen (or the chosen address was deleted): the default address, which the list puts first.
    const address = chosen ?? (postcode ? null : addresses?.[0] ?? null);
    const heading = user && customerId != null ? `Deliver to ${firstName(address?.name ?? user.name)}` : 'Deliver to';
    const place = address ? `${address.city} ${address.postcode}` : postcode ? placeOf(postcode) : 'Update location';
    return { heading, place, known: !!(address || postcode), address, postcode, addresses, open, chooseAddress, choosePostcode,
      syncAddresses: setAddresses };
  }, [choice, customerId, addresses, user, open, chooseAddress, choosePostcode]);

  return (
    <Ctx.Provider value={value}>
      {children}
      {dialogOpen && <DeliverToDialog onClose={() => setDialogOpen(false)} />}
    </Ctx.Provider>
  );
}

export function useDeliverTo(): DeliverTo {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useDeliverTo must be used inside DeliverToProvider');
  return ctx;
}
