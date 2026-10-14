import { toSession } from './auth.service';

describe('toSession', () => {
  it('keeps the signed-in profile and does not carry a password field', () => {
    const session = toSession({
      id: 7,
      username: 'cashier',
      fullName: 'Counter Cashier',
      email: 'cashier@example.com',
      phone: null,
      status: 'ACTIVE',
      mustChangePassword: true,
      roles: [{ id: 2, code: 'CASHIER', name: 'Cashier' }],
      permissions: ['SALES_CREATE', 'SALES_VIEW'],
    });

    expect(session).toEqual({
      id: 7,
      username: 'cashier',
      fullName: 'Counter Cashier',
      email: 'cashier@example.com',
      phone: null,
      status: 'ACTIVE',
      roles: ['CASHIER'],
      permissions: ['SALES_CREATE', 'SALES_VIEW'],
      lastLoginAt: null,
      mustChangePassword: true,
    });
    expect(session).not.toHaveProperty('password');
    expect(session).not.toHaveProperty('passwordHash');
  });

  it('treats missing permission and role lists as empty', () => {
    const session = toSession({
      id: 1,
      username: 'admin',
      fullName: 'Administrator',
      email: 'admin@example.com',
      status: 'ACTIVE',
      mustChangePassword: false,
      roles: undefined as never,
      permissions: undefined as never,
    });

    expect(session.roles).toEqual([]);
    expect(session.permissions).toEqual([]);
  });
});
