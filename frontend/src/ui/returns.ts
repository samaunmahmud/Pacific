import type { ReturnReason, ReturnStatus } from '../api/types';

export const RETURN_REASONS: [ReturnReason, string][] = [
  ['DAMAGED', 'Arrived damaged or faulty'],
  ['NOT_AS_DESCRIBED', 'Not as described'],
  ['WRONG_ITEM', 'Wrong item received'],
  ['NO_LONGER_NEEDED', 'No longer needed'],
  ['OTHER', 'Other'],
];

export const RETURN_STATUS_LABEL: Record<ReturnStatus, string> = {
  REQUESTED: 'Return requested',
  APPROVED: 'Return approved',
  REJECTED: 'Return declined',
  REFUNDED: 'Refunded',
  CANCELLED: 'Withdrawn',
};
